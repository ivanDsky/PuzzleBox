package com.ivandsky.puzzlebox.games.zip.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipCommand
import com.ivandsky.puzzlebox.games.zip.ZipPath
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Zip session codec v2 — the §4 reader rules applied to the play session, field set
 * {ref, path, hintsUsed, elapsedMs, completedAt} plus the §10 mistake/backtrack counters,
 * the timerStarted bit and (v2) the undo/redo command history. Layout (i64 fields
 * little-endian, the u16 lengths big-endian):
 *
 * ```
 * 0   u8    formatVersion = 2
 *     u16   puzzleLen; puzzleLen x u8   (ZipCodec bytes: the puzzle rides by VALUE so an
 *                                        in-progress day survives offline replay, F2 §2)
 *     u8    flags                       (bit0 completedAt present, bit1 ref present,
 *                                        bit2 timerStarted)
 *     [bit1] i64 epochDay, u8 numberPresent, i64 puzzleNumber
 *     u8    pathLen; pathLen x u8 cellIdx
 *     u8    hintsUsed; u8 mistakeCount; u8 backtrackCount
 *     i64   elapsedMs
 *     [bit0] i64 completedAt epochMilli
 *     u16   doneLen; doneLen x command         (v2+; oldest first)
 *     u16   undoneLen; undoneLen x command     (v2+; redo stack, next redo LAST)
 *
 * command := u8 tag
 *   0 Extend    u8 cell
 *   1 RetractTo u8 target; u8 tailLen; tailLen x u8 cell
 *   2 Reset     u8 pathLen; pathLen x u8 cell
 * ```
 *
 * v1 is the same layout without the history sections; it decodes with an empty history.
 * Bytes after the last known field are the A19 [ZipSession.unknownTail].
 */
object ZipSessionCodec : GameSessionCodec<ZipSession> {

    const val VERSION: Int = 2

    /** Decode-side ceiling on done + undone commands; larger blobs are corrupt, not played. */
    const val MAX_HISTORY: Int = 10_000

    override fun serialize(session: ZipSession): ByteArray {
        val puzzleBytes = ZipCodec.encode(session.puzzle) // canonical, self-describing (A19 nesting)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }
        require(session.path.size in 0..255) { "layout carries u8 path length" }
        require(session.hintsUsed in 0..255) { "layout carries u8 hintsUsed" }
        require(session.mistakeCount in 0..255) { "layout carries u8 mistakeCount" }
        require(session.backtrackCount in 0..255) { "layout carries u8 backtrackCount" }
        val history = session.history
        require(history.done.size + history.undone.size <= MAX_HISTORY) { "history exceeds $MAX_HISTORY commands" }

        val out = ByteArrayOutputStream(puzzleBytes.size + 32)
        out.write(VERSION)
        out.write((puzzleBytes.size shr 8) and 0xFF)
        out.write(puzzleBytes.size and 0xFF)
        out.write(puzzleBytes)

        var flags = 0
        if (session.completedAt != null) flags = flags or FLAG_COMPLETED
        if (session.ref != null) flags = flags or FLAG_REF
        if (session.timerStarted) flags = flags or FLAG_TIMER_STARTED
        out.write(flags)

        session.ref?.let { ref ->
            writeI64(out, ref.date.toEpochDay()) // fits i32 for any plausible calendar, kept i64-simple
            val number = ref.puzzleNumber
            if (number == null) {
                out.write(0)
            } else {
                out.write(1)
                writeI64(out, number.toLong())
            }
        }

        out.write(session.path.size)
        session.path.forEach { out.write(it.idx) }
        out.write(session.hintsUsed)
        out.write(session.mistakeCount)
        out.write(session.backtrackCount)
        writeI64(out, session.elapsedMs)
        session.completedAt?.let { writeI64(out, it.toEpochMilli()) }
        writeCommands(out, history.done)
        writeCommands(out, history.undone)
        // A19: the tail a newer reader added travels verbatim so decode∘encode is lossless.
        out.write(session.unknownTail)
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<ZipSession> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version != 1 && version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val puzzleLen = cursor.u16() ?: return malformed("truncated puzzle length")
        val puzzleBytes = cursor.bytes(puzzleLen) ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = ZipCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
        }

        val flags = cursor.u8() ?: return malformed("truncated session flags")
        val hasCompletedAt = flags and FLAG_COMPLETED != 0
        val hasRef = flags and FLAG_REF != 0
        val timerStarted = flags and FLAG_TIMER_STARTED != 0

        var ref: ZipPuzzleRef? = null
        if (hasRef) {
            val epochDay = cursor.i64() ?: return malformed("truncated ref date")
            val hasNumber = cursor.u8() ?: return malformed("truncated ref number flag")
            val number = if (hasNumber == 1) {
                cursor.i64() ?: return malformed("truncated ref number")
            } else {
                null
            }
            ref = ZipPuzzleRef(java.time.LocalDate.ofEpochDay(epochDay), number?.toInt())
        }

        val pathLen = cursor.u8() ?: return malformed("truncated path length")
        val path = ArrayList<Cell>(pathLen)
        repeat(pathLen) {
            val cellIdx = cursor.u8() ?: return malformed("truncated path cell")
            if (cellIdx >= puzzle.board.cellCount) return malformed("path cell $cellIdx out of range")
            path += Cell(cellIdx)
        }

        val hintsUsed = cursor.u8() ?: return malformed("truncated hints")
        val mistakes = cursor.u8() ?: return malformed("truncated mistakes")
        val backtracks = cursor.u8() ?: return malformed("truncated backtracks")
        val elapsedMs = cursor.i64() ?: return malformed("truncated elapsedMs")
        val completedAt = if (hasCompletedAt) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }

        var history = CommandHistory<ZipPath, ZipCommand>()
        if (version >= 2) {
            val cellCount = puzzle.board.cellCount
            val done = readCommands(cursor, cellCount) ?: return malformed("malformed command history")
            val undone = readCommands(cursor, cellCount) ?: return malformed("malformed redo history")
            if (done.size + undone.size > MAX_HISTORY) return malformed("history exceeds $MAX_HISTORY commands")
            if (!replaysTo(done, path)) return malformed("command history does not reproduce the path")
            history = CommandHistory(done, undone)
        }

        // A19: unknown trailing bytes belong to a newer schema version — carry them, never
        // reject them; serialize re-emits them verbatim.
        val unknownTail = cursor.remaining()
        // runningSince is a runtime anchor, never persisted: restore paused at elapsedMs.
        return DecodeResult.Ok(
            ZipSession(
                ref = ref,
                puzzle = puzzle,
                path = path,
                hintsUsed = hintsUsed,
                mistakeCount = mistakes,
                backtrackCount = backtracks,
                elapsedMs = elapsedMs,
                timerStarted = timerStarted,
                runningSince = null,
                completedAt = completedAt,
                history = history,
                unknownTail = unknownTail,
            ),
        )
    }

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2
    private const val FLAG_TIMER_STARTED = 4

    private const val TAG_EXTEND = 0
    private const val TAG_RETRACT_TO = 1
    private const val TAG_RESET = 2

    private fun writeCommands(out: ByteArrayOutputStream, commands: List<ZipCommand>) {
        out.write((commands.size shr 8) and 0xFF)
        out.write(commands.size and 0xFF)
        commands.forEach { command ->
            when (command) {
                is ZipCommand.Extend -> {
                    out.write(TAG_EXTEND)
                    out.write(command.cell.idx)
                }

                is ZipCommand.RetractTo -> {
                    out.write(TAG_RETRACT_TO)
                    out.write(command.target.idx)
                    writeCells(out, command.removedTail)
                }

                is ZipCommand.Reset -> {
                    out.write(TAG_RESET)
                    writeCells(out, command.removedPath)
                }
            }
        }
    }

    private fun writeCells(out: ByteArrayOutputStream, cells: List<Cell>) {
        require(cells.size in 0..255) { "layout carries u8 command cell counts" }
        out.write(cells.size)
        cells.forEach { out.write(it.idx) }
    }

    /** Null on truncation, an unknown tag, a cell outside the board, or an oversized count. */
    private fun readCommands(cursor: Cursor, cellCount: Int): List<ZipCommand>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val commands = ArrayList<ZipCommand>(count)
        repeat(count) {
            val command = when (cursor.u8() ?: return null) {
                TAG_EXTEND -> ZipCommand.Extend(cursor.cell(cellCount) ?: return null)
                TAG_RETRACT_TO -> {
                    val target = cursor.cell(cellCount) ?: return null
                    ZipCommand.RetractTo(target, cursor.cells(cellCount) ?: return null)
                }

                TAG_RESET -> ZipCommand.Reset(cursor.cells(cellCount) ?: return null)
                else -> return null
            }
            commands += command
        }
        return commands
    }

    /** The done stack must be a real timeline: reverting it from [path] and re-applying it
     *  forward lands back on [path], otherwise an undo would corrupt the board. */
    private fun replaysTo(done: List<ZipCommand>, path: List<Cell>): Boolean {
        var state = path
        for (command in done.asReversed()) state = command.revert(state)
        for (command in done) state = command.apply(state)
        return state == path
    }

    private fun writeI64(out: ByteArrayOutputStream, value: Long) {
        repeat(8) { shift -> out.write(((value shr (shift * 8)) and 0xFF).toInt()) }
    }

    private fun malformed(detail: String): DecodeResult.Err =
        DecodeResult.Err(DecodeError.Malformed(detail))

    private class Cursor(private val bytes: ByteArray) {
        var pos = 0

        fun u8(): Int? = bytes.getOrNull(pos++)?.toInt()?.and(0xFF)

        fun u16(): Int? {
            val hi = u8() ?: return null
            val lo = u8() ?: return null
            return (hi shl 8) + lo
        }

        fun i64(): Long? {
            var value = 0L
            repeat(8) { shift ->
                val byte = u8() ?: return null
                value = value or (byte.toLong() shl (shift * 8))
            }
            return value
        }

        fun bytes(len: Int): ByteArray? {
            if (pos + len > bytes.size) return null
            return bytes.copyOfRange(pos, pos + len).also { pos += len }
        }

        fun cell(cellCount: Int): Cell? {
            val idx = u8() ?: return null
            return if (idx < cellCount) Cell(idx) else null
        }

        /** u8 count then that many in-range cells. */
        fun cells(cellCount: Int): List<Cell>? {
            val len = u8() ?: return null
            val cells = ArrayList<Cell>(len)
            repeat(len) { cells += cell(cellCount) ?: return null }
            return cells
        }

        fun remaining(): ByteArray = bytes.copyOfRange(pos, bytes.size)
    }
}
