package com.ivandsky.puzzlebox.games.queens.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.PuzzleKeyTag
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensCommand
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant

/**
 * Queens session codec (zip ZipSessionCodec shape, A19): the puzzle rides BY VALUE so an
 * in-progress day survives offline replay. v4 (ADR-0018, spec A11) tags the ref with its
 * [PuzzleKey] kind; v3 refs are untagged dailies. v3+ persists the undo/redo COMMAND history
 * and the banked clock; AUTO marks are derived on decode, never stored. v2 blobs still read
 * (snapshots dropped → empty history, elapsed from startedAt→completedAt when won, else 0);
 * v1 and unknown versions → UnsupportedVersion. Layout (i64 little-endian, u16 big-endian):
 *
 * ```
 * 0   u8    formatVersion = 4
 *     u16   puzzleLen; puzzleLen x u8   (QueensCodec bytes)
 *     u8    flags   (bit0 completedAt, bit1 ref, bit2 unused, bit3 retired)
 *     [bit1] u8 keyTag (absent in v2/v3: read as 0), then
 *            0 Daily  i64 epochDay, u8 numberPresent, [i64 puzzleNumber]
 *            1 Custom u8 idLen (1..255); idLen x u8 puzzleId (UTF-8)
 *     u8 queensLen; x u8 cellIdx (placement order) · u16 manualLen; x u8 cellIdx (MANUAL, asc)
 *     u16 doneLen; x command (oldest first) · u16 undoneLen; x command (redo, bottom first)
 *     [bit0] i64 completedAt epochMilli · i64 elapsedMs (banked play time) · u8 hintsUsed
 *
 * command = u8 tag, then 0 PlaceQueen u8 cell · 1 RemoveQueen u8 cell, u8 index, u8 hadMark
 *   · 2 ToggleCross u8 cell, u8 wasMarked · 3 Clear u8 n; n x u8 cell, u16 m; m x u8 cell
 * ```
 *
 * v2 (read-only) was `... queens, marks{idx, kind, [source]}, undo snapshots, [bit0]
 * completedAt, [bit2] startedAt, hintsUsed`. Decode validates cell ranges and bounds each
 * history list by [MAX_HISTORY].
 *
 * Flags bit3 (8) once marked a session whose Auto-cross was OFF. Auto-cross is now a per-game
 * setting (`game.queens.auto_cross`), so [serialize] never sets the bit and decode ignores it:
 * an old save with it set still reads, and the setting decides how the board derives its marks.
 */
object QueensSessionCodec : GameSessionCodec<QueensSession> {

    const val VERSION: Int = 4

    /** The oldest format still readable (v2: snapshot undo, wall-clock startedAt). */
    private const val V2: Int = 2

    /** Decode bound on each history list; [serialize] keeps only the newest this many. */
    const val MAX_HISTORY: Int = 10_000

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2
    private const val FLAG_V2_STARTED = 4

    private const val KIND_MANUAL = 0
    private const val KIND_AUTO = 1

    private const val CMD_PLACE = 0
    private const val CMD_REMOVE = 1
    private const val CMD_TOGGLE = 2
    private const val CMD_CLEAR = 3

    /** The first format whose ref carries a [PuzzleKeyTag]; older refs are untagged dailies. */
    private const val V_KEY_TAG: Int = 4

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
        out.write(flags)

        session.ref?.let { writeKey(out, it.key) }

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
        val cursor = SessionCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version !in V2..VERSION) {
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
        val ref = if (flags and FLAG_REF == 0) {
            null
        } else {
            when (val key = readKey(cursor, version)) {
                is DecodeResult.Ok -> QueensPuzzleRef(key.value)
                is DecodeResult.Err -> return key
            }
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
        cursor: SessionCursor,
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
            ),
        )
    }

    /**
     * The v2 tail, read for compatibility only: its marks carry provenance (only the
     * MANUAL ones are stored truth now) and its snapshots are parsed — keeping the
     * structural validation — then dropped; a v2 save resumes with an empty history.
     */
    private fun readV2Tail(
        cursor: SessionCursor,
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
            ),
        )
    }

    private fun writeKey(out: ByteArrayOutputStream, key: PuzzleKey) =
        PuzzleKeyTag.write(key, out::write) { daily ->
            writeI64(out, daily.date.toEpochDay())
            val number = daily.puzzleNumber
            out.write(if (number == null) 0 else 1)
            number?.let { writeI64(out, it.toLong()) }
        }

    private fun readKey(cursor: SessionCursor, version: Int): DecodeResult<PuzzleKey> =
        PuzzleKeyTag.read(version, taggedSince = V_KEY_TAG, u8 = cursor::u8) {
            val epochDay = cursor.i64() ?: return@read malformed("truncated ref date")
            val number = when (cursor.u8()) {
                0 -> null
                1 -> cursor.i64() ?: return@read malformed("truncated ref number")
                else -> return@read malformed("malformed ref number flag")
            }
            PuzzleKeyTag.daily(epochDay, number)
        }

    /** Ascending cell order for determinism. */
    private fun writeCells(out: ByteArrayOutputStream, cells: Collection<Cell>) {
        require(cells.size <= 0xFFFF) { "v3 layout carries u16 cell counts" }
        writeU16(out, cells.size)
        cells.map { it.idx }.sorted().forEach { out.write(it) }
    }

    private fun readCells(cursor: SessionCursor, cellRange: IntRange): Set<Cell>? {
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
    private fun readCommands(cursor: SessionCursor, cellRange: IntRange): List<QueensCommand>? {
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

    private fun cell(cursor: SessionCursor, cellRange: IntRange): Cell? =
        cursor.u8()?.takeIf { it in cellRange }?.let(::Cell)

    private fun flag(cursor: SessionCursor): Boolean? = when (cursor.u8()) {
        0 -> false
        1 -> true
        else -> null
    }

    /**
     * v2 marks → cell → kind. AUTO marks are only validated (their source must stand,
     * A10) and discarded: v3 derives them.
     */
    private fun readV2Marks(
        cursor: SessionCursor,
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
}
