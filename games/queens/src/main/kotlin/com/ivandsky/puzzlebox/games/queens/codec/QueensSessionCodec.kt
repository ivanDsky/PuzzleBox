package com.ivandsky.puzzlebox.games.queens.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensCommand
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Queens session codec (zip ZipSessionCodec shape, A19; pre-release): the puzzle rides
 * BY VALUE so an in-progress day survives offline replay. v3 (command-pattern undo,
 * 2026-11-03) persists the undo/redo COMMAND history and the banked clock instead of
 * v2's whole-board snapshots + wall-clock startedAt; AUTO marks are no longer stored at
 * all — they are derived from queens + manual marks + the Auto-cross setting on decode.
 * v2 blobs still read (snapshots dropped → empty history, elapsed derived from
 * startedAt→completedAt when the win was stamped, else 0); v1 and unknown versions are
 * rejected as UnsupportedVersion and degrade to a fresh fetch. Layout (i64
 * little-endian, u16 big-endian):
 *
 * ```
 * 0   u8    formatVersion = 3
 *     u16   puzzleLen; puzzleLen x u8   (QueensCodec bytes)
 *     u8    flags                       (bit0 = completedAt present, bit1 = ref present,
 *                                       bit2 = unused, bit3 = autoCross OFF)
 *     [bit1] i64 epochDay, u8 numberPresent, i64 puzzleNumber
 *     u8    queensLen; queensLen x u8 cellIdx          (placement order)
 *     u16   manualLen; manualLen x u8 cellIdx          (MANUAL marks, ascending)
 *     u16   doneLen;   doneLen   x command             (oldest first)
 *     u16   undoneLen; undoneLen x command             (redo stack, bottom first)
 *     [bit0] i64 completedAt epochMilli
 *     i64   elapsedMs                                  (banked play time)
 *     u8    hintsUsed
 *
 * command = u8 tag, then
 *   0 PlaceQueen   u8 cell
 *   1 RemoveQueen  u8 cell, u8 index, u8 hadMark(0/1)
 *   2 ToggleCross  u8 cell, u8 wasMarked(0/1)
 *   3 Clear        u8 queensLen; queensLen x u8 cell, u16 manualLen; manualLen x u8 cell
 * ```
 *
 * v2 (read-only now) was `... queens, marks{idx, kind, [source]}, undo snapshots, [bit0]
 * completedAt, [bit2] startedAt, hintsUsed` with flag bit2 = startedAt present.
 *
 * Decode validates cell ranges and bounds each history list by [MAX_HISTORY].
 */
object QueensSessionCodec : GameSessionCodec<QueensSession> {

    const val VERSION: Int = 3

    /** The oldest format still readable (v2: snapshot undo, wall-clock startedAt). */
    private const val V2: Int = 2

    /** Decode bound on each history list; [serialize] keeps only the newest this many. */
    const val MAX_HISTORY: Int = 10_000

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2
    private const val FLAG_V2_STARTED = 4
    private const val FLAG_AUTO_CROSS_OFF = 8

    private const val KIND_MANUAL = 0
    private const val KIND_AUTO = 1

    private const val CMD_PLACE = 0
    private const val CMD_REMOVE = 1
    private const val CMD_TOGGLE = 2
    private const val CMD_CLEAR = 3

    override fun serialize(session: QueensSession): ByteArray {
        val puzzleBytes = QueensCodec.encode(session.puzzle) // canonical, self-describing (A19 nesting)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }
        require(session.queens.size <= 0xFF) { "v3 layout carries u8 queen count" }

        val out = ByteArrayOutputStream(puzzleBytes.size + 32)
        out.write(VERSION)
        out.write((puzzleBytes.size shr 8) and 0xFF)
        out.write(puzzleBytes.size and 0xFF)
        out.write(puzzleBytes)

        var flags = 0
        if (session.completedAt != null) flags = flags or FLAG_COMPLETED
        if (session.ref != null) flags = flags or FLAG_REF
        if (!session.autoCross) flags = flags or FLAG_AUTO_CROSS_OFF
        out.write(flags)

        session.ref?.let { ref ->
            writeI64(out, ref.date.toEpochDay())
            val number = ref.puzzleNumber
            if (number == null) {
                out.write(0)
            } else {
                out.write(1)
                writeI64(out, number.toLong())
            }
        }

        out.write(session.queens.size)
        session.queens.forEach { out.write(it.idx) }
        writeCells(out, session.board.manual)

        writeCommands(out, session.history.done)
        writeCommands(out, session.history.undone)

        session.completedAt?.let { writeI64(out, it.toEpochMilli()) }
        writeI64(out, session.elapsedMs)
        out.write(session.hintsUsed)
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<QueensSession> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version != VERSION && version != V2) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val puzzleLen = cursor.u16() ?: return malformed("truncated puzzle length")
        val puzzleBytes = cursor.bytes(puzzleLen) ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = QueensCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
        }
        val cellRange = 0 until puzzle.cellCount

        val flags = cursor.u8() ?: return malformed("truncated session flags")
        var ref: QueensPuzzleRef? = null
        if (flags and FLAG_REF != 0) {
            val epochDay = cursor.i64() ?: return malformed("truncated ref date")
            val hasNumber = cursor.u8() ?: return malformed("truncated ref number flag")
            val number = if (hasNumber == 1) {
                cursor.i64() ?: return malformed("truncated ref number")
            } else {
                null
            }
            ref = QueensPuzzleRef(LocalDate.ofEpochDay(epochDay), number?.toInt())
        }

        val queensLen = cursor.u8() ?: return malformed("truncated queen count")
        val queens = mutableListOf<Cell>()
        repeat(queensLen) {
            val idx = cursor.u8() ?: return malformed("truncated queen cell")
            if (idx !in cellRange) return malformed("queen cell $idx outside board")
            queens.add(Cell(idx))
        }

        return if (version == V2) {
            readV2Tail(cursor, flags, cellRange, ref, puzzle, queens)
        } else {
            readV3Tail(cursor, flags, cellRange, ref, puzzle, queens)
        }
    }

    private fun readV3Tail(
        cursor: Cursor,
        flags: Int,
        cellRange: IntRange,
        ref: QueensPuzzleRef?,
        puzzle: QueensPuzzle,
        queens: List<Cell>,
    ): DecodeResult<QueensSession> {
        val manual = readCells(cursor, cellRange) ?: return malformed("malformed manual marks")
        val done = readCommands(cursor, cellRange) ?: return malformed("malformed undo history")
        val undone = readCommands(cursor, cellRange) ?: return malformed("malformed redo history")

        val completedAt = if (flags and FLAG_COMPLETED != 0) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }
        val elapsedMs = cursor.i64() ?: return malformed("truncated elapsedMs")
        if (elapsedMs < 0) return malformed("negative elapsedMs")
        val hintsUsed = cursor.u8() ?: return malformed("truncated hintsUsed")

        return DecodeResult.Ok(
            QueensSession.restore(
                ref = ref,
                puzzle = puzzle,
                board = QueensBoard(queens, manual),
                history = CommandHistory(done, undone),
                completedAt = completedAt,
                elapsedMs = elapsedMs,
                hintsUsed = hintsUsed,
                autoCross = flags and FLAG_AUTO_CROSS_OFF == 0,
            ),
        )
    }

    /**
     * The v2 tail, read for compatibility only: its marks carry provenance (only the
     * MANUAL ones are stored truth now) and its snapshots are parsed — keeping the
     * structural validation — then dropped; a v2 save resumes with an empty history.
     */
    private fun readV2Tail(
        cursor: Cursor,
        flags: Int,
        cellRange: IntRange,
        ref: QueensPuzzleRef?,
        puzzle: QueensPuzzle,
        queens: List<Cell>,
    ): DecodeResult<QueensSession> {
        val marks = when (val decoded = readV2Marks(cursor, cellRange, queens)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
        }

        val undoLen = cursor.u16() ?: return malformed("truncated undo depth")
        repeat(undoLen) {
            val snapshotQueensLen = cursor.u8() ?: return malformed("truncated snapshot queen count")
            val snapshotQueens = mutableListOf<Cell>()
            repeat(snapshotQueensLen) {
                val idx = cursor.u8() ?: return malformed("truncated snapshot queen cell")
                if (idx !in cellRange) return malformed("snapshot queen cell $idx outside board")
                snapshotQueens.add(Cell(idx))
            }
            val snapshotMarks = readV2Marks(cursor, cellRange, snapshotQueens)
            if (snapshotMarks is DecodeResult.Err) return snapshotMarks
        }

        val completedAt = if (flags and FLAG_COMPLETED != 0) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }
        val startedAt = if (flags and FLAG_V2_STARTED != 0) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated startedAt"))
        } else {
            null
        }
        val hintsUsed = cursor.u8() ?: return malformed("truncated hintsUsed")

        // v2 froze the duration only at the win (first action → win stamp); an unfinished
        // v2 save has no banked time to carry.
        val elapsedMs = if (startedAt != null && completedAt != null) {
            Duration.between(startedAt, completedAt).toMillis().coerceAtLeast(0L)
        } else {
            0L
        }

        return DecodeResult.Ok(
            QueensSession.restore(
                ref = ref,
                puzzle = puzzle,
                board = QueensBoard(queens, marks.filterValues { it == KIND_MANUAL }.keys),
                history = CommandHistory(),
                completedAt = completedAt,
                elapsedMs = elapsedMs,
                hintsUsed = hintsUsed,
                autoCross = flags and FLAG_AUTO_CROSS_OFF == 0,
            ),
        )
    }

    /** Ascending cell order for determinism. */
    private fun writeCells(out: ByteArrayOutputStream, cells: Collection<Cell>) {
        require(cells.size <= 0xFFFF) { "v3 layout carries u16 cell counts" }
        writeU16(out, cells.size)
        cells.map { it.idx }.sorted().forEach { out.write(it) }
    }

    private fun readCells(cursor: Cursor, cellRange: IntRange): Set<Cell>? {
        val count = cursor.u16() ?: return null
        if (count > cellRange.last + 1) return null // before allocating anything proportional to it
        val cells = LinkedHashSet<Cell>(count)
        repeat(count) {
            val idx = cursor.u8() ?: return null
            if (idx !in cellRange || !cells.add(Cell(idx))) return null
        }
        return cells
    }

    private fun writeCommands(out: ByteArrayOutputStream, commands: List<QueensCommand>) {
        val kept = commands.takeLast(MAX_HISTORY) // oldest fall off: undo depth shrinks, state never changes
        writeU16(out, kept.size)
        kept.forEach { command ->
            when (command) {
                is QueensCommand.PlaceQueen -> {
                    out.write(CMD_PLACE)
                    out.write(command.cell.idx)
                }

                is QueensCommand.RemoveQueen -> {
                    out.write(CMD_REMOVE)
                    out.write(command.cell.idx)
                    out.write(command.index)
                    out.write(if (command.hadMark) 1 else 0)
                }

                is QueensCommand.ToggleCross -> {
                    out.write(CMD_TOGGLE)
                    out.write(command.cell.idx)
                    out.write(if (command.wasMarked) 1 else 0)
                }

                is QueensCommand.Clear -> {
                    require(command.queens.size <= 0xFF) { "v3 layout carries u8 queen count" }
                    out.write(CMD_CLEAR)
                    out.write(command.queens.size)
                    command.queens.forEach { out.write(it.idx) }
                    writeCells(out, command.manual)
                }
            }
        }
    }

    /** Null = malformed (truncated, out-of-range, unknown tag, or past [MAX_HISTORY]). */
    private fun readCommands(cursor: Cursor, cellRange: IntRange): List<QueensCommand>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val commands = ArrayList<QueensCommand>(count)
        repeat(count) {
            val command: QueensCommand = when (cursor.u8() ?: return null) {
                CMD_PLACE -> QueensCommand.PlaceQueen(cell(cursor, cellRange) ?: return null)

                CMD_REMOVE -> {
                    val cell = cell(cursor, cellRange) ?: return null
                    val index = cursor.u8() ?: return null
                    if (index > cellRange.last) return null
                    val hadMark = flag(cursor) ?: return null
                    QueensCommand.RemoveQueen(cell, index, hadMark)
                }

                CMD_TOGGLE -> {
                    val cell = cell(cursor, cellRange) ?: return null
                    QueensCommand.ToggleCross(cell, wasMarked = flag(cursor) ?: return null)
                }

                CMD_CLEAR -> {
                    val queensLen = cursor.u8() ?: return null
                    val queens = ArrayList<Cell>(queensLen)
                    repeat(queensLen) { queens.add(cell(cursor, cellRange) ?: return null) }
                    QueensCommand.Clear(queens, readCells(cursor, cellRange) ?: return null)
                }

                else -> return null
            }
            commands.add(command)
        }
        return commands
    }

    private fun cell(cursor: Cursor, cellRange: IntRange): Cell? =
        cursor.u8()?.takeIf { it in cellRange }?.let(::Cell)

    private fun flag(cursor: Cursor): Boolean? = when (cursor.u8()) {
        0 -> false
        1 -> true
        else -> null
    }

    /**
     * v2 marks → cell → kind. AUTO marks are only validated (their source must stand,
     * A10) and discarded: v3 derives them.
     */
    private fun readV2Marks(
        cursor: Cursor,
        cellRange: IntRange,
        sourceQueens: List<Cell>,
    ): DecodeResult<Map<Cell, Int>> {
        val marksLen = cursor.u16() ?: return malformed("truncated mark count")
        val marks = LinkedHashMap<Cell, Int>(marksLen)
        repeat(marksLen) {
            val idx = cursor.u8() ?: return malformed("truncated mark cell")
            if (idx !in cellRange) return malformed("mark cell $idx outside board")
            if (Cell(idx) in marks) return malformed("duplicate mark cell $idx")
            val kind = cursor.u8() ?: return malformed("truncated mark kind")
            when (kind) {
                KIND_MANUAL -> Unit
                KIND_AUTO -> {
                    val source = cursor.u8() ?: return malformed("truncated auto source")
                    if (source !in cellRange) return malformed("auto source $source outside board")
                    if (Cell(source) !in sourceQueens) return malformed("AUTO mark sourced by non-standing queen $source")
                }

                else -> return malformed("unknown mark kind $kind")
            }
            marks[Cell(idx)] = kind
        }
        return DecodeResult.Ok(marks)
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
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
    }
}
