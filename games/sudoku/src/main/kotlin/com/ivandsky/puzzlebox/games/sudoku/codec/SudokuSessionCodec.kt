package com.ivandsky.puzzlebox.games.sudoku.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCommandCodec.Reader
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Session bytes v2, puzzle embedded by value so an unfinished day survives offline.
 * Multi-byte fields are little-endian; `cell` and `command` are [SudokuCommandCodec]'s:
 *
 * ```
 * 0   u8    formatVersion = 2
 *     u16   puzzleLen; puzzleLen x u8     (SudokuCodec bytes)
 *     u8    flags                         (bit0 completedAt, bit1 ref, bit2 notesMode)
 *     [bit1] u8 keyTag                    (v2+; v1 carries no tag and is always Daily)
 *            u8 gameIdLen; ASCII gameId
 *       0 Daily   i64 epochDay, u8 hasNumber, [hasNumber] i32 puzzleNumber
 *       1 Custom  u8 idLen (1..255); idLen x u8 UTF-8 puzzleId
 *     N² x cell                           (row-major)
 *     u16   doneCount; doneCount x command        (undo stack, oldest first)
 *     u16   undoneCount; undoneCount x command    (redo stack, bottom first)
 *     [bit0] i64 completedAt epochMilli
 *     i64   elapsedMs
 *     i32   hintsUsed
 * ```
 *
 * Decode never throws. Anything truncated, out of range, inconsistent with the puzzle's givens,
 * followed by trailing bytes, or whose history does not walk back and forth from the stored
 * board is [DecodeResult.Err], which the caller treats as a fresh start.
 */
object SudokuSessionCodec : GameSessionCodec<SudokuSession> {

    const val VERSION: Int = 2

    /** Decode bound on each history list; [serialize] keeps only the newest this many. */
    const val MAX_HISTORY: Int = 10_000

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2
    private const val FLAG_NOTES_MODE = 4
    private const val KNOWN_FLAGS = FLAG_COMPLETED or FLAG_REF or FLAG_NOTES_MODE

    private const val KEY_DAILY = 0
    private const val KEY_CUSTOM = 1

    override fun serialize(session: SudokuSession): ByteArray {
        val puzzleBytes = SudokuCodec.encode(session.puzzle)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }
        val out = ByteArrayOutputStream(puzzleBytes.size + 3 * session.board.cells.size + 48)
        out.write(VERSION)
        out.writeLe(puzzleBytes.size.toLong(), bytes = 2)
        out.write(puzzleBytes)

        var flags = 0
        if (session.completedAt != null) flags = flags or FLAG_COMPLETED
        if (session.ref != null) flags = flags or FLAG_REF
        if (session.notesMode) flags = flags or FLAG_NOTES_MODE
        out.write(flags)

        session.ref?.let { ref ->
            val key = ref.key
            out.write(if (key is PuzzleKey.Custom) KEY_CUSTOM else KEY_DAILY)
            val id = ref.gameId.value.toByteArray(Charsets.US_ASCII)
            require(id.size in 1..0xFF) { "gameId must be 1..255 ASCII chars" }
            out.write(id.size)
            out.write(id)
            when (key) {
                is PuzzleKey.Daily -> {
                    out.writeLe(key.date.toEpochDay(), bytes = 8)
                    val number = key.puzzleNumber
                    out.write(if (number == null) 0 else 1)
                    if (number != null) out.writeLe(number.toLong(), bytes = 4)
                }
                is PuzzleKey.Custom -> {
                    val code = key.puzzleId.encodeToByteArray()
                    require(code.size in 1..0xFF) { "layout carries a u8 puzzleId length" }
                    out.write(code.size)
                    out.write(code)
                }
            }
        }

        session.board.cells.forEach { SudokuCommandCodec.writeCell(out, it) }
        writeHistory(out, session.history.done)
        writeHistory(out, session.history.undone)
        session.completedAt?.let { out.writeLe(it.toEpochMilli(), bytes = 8) }
        out.writeLe(session.elapsedMs, bytes = 8)
        out.writeLe(session.hintsUsed.toLong(), bytes = 4)
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<SudokuSession> {
        val reader = Reader(bytes)
        val version = reader.u8() ?: return malformed("empty session blob")
        if (version !in 1..VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))

        val puzzleLen = reader.u16() ?: return malformed("truncated puzzle length")
        val puzzleBytes = reader.bytes(puzzleLen) ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = SudokuCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return decoded
        }

        val flags = reader.u8() ?: return malformed("truncated flags")
        if (flags and KNOWN_FLAGS.inv() != 0) return malformed("unknown flag bits in $flags")
        val ref = if (flags and FLAG_REF != 0) readRef(reader, version) ?: return malformed("malformed ref") else null

        val cells = ArrayList<SudokuCell>(puzzle.geometry.cellCount)
        for (idx in 0 until puzzle.geometry.cellCount) {
            cells += readCell(reader, puzzle, idx) ?: return malformed("malformed cell $idx")
        }
        val board = SudokuBoard(puzzle.geometry, cells)

        val done = when (val decoded = readHistory(reader, puzzle, "undo")) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return decoded
        }
        val undone = when (val decoded = readHistory(reader, puzzle, "redo")) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return decoded
        }
        val history = CommandHistory<SudokuBoard, SudokuCommand>(done, undone)
        if (!walksFrom(board, history)) return malformed("history does not walk from the stored board")

        val completedAt = if (flags and FLAG_COMPLETED != 0) {
            Instant.ofEpochMilli(reader.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }
        if ((completedAt != null) != SudokuConflicts.isSolved(board)) return malformed("completion disagrees with the board")
        val elapsedMs = reader.i64()?.takeIf { it >= 0 } ?: return malformed("missing or negative elapsedMs")
        val hintsUsed = reader.i32()?.takeIf { it >= 0 } ?: return malformed("missing or negative hintsUsed")
        if (!reader.atEnd()) return malformed("trailing bytes after session")

        return DecodeResult.Ok(
            SudokuSession(
                ref = ref,
                puzzle = puzzle,
                board = board,
                history = history,
                completedAt = completedAt,
                elapsedMs = elapsedMs,
                hintsUsed = hintsUsed,
                notesMode = flags and FLAG_NOTES_MODE != 0,
            ),
        )
    }

    private fun readRef(reader: Reader, version: Int): SudokuPuzzleRef? {
        val tag = if (version >= 2) reader.u8() ?: return null else KEY_DAILY
        val idLen = reader.u8()?.takeIf { it > 0 } ?: return null
        val id = reader.bytes(idLen)?.takeIf { raw -> raw.all { it >= 0 } } ?: return null
        val key = when (tag) {
            KEY_DAILY -> readDaily(reader)
            KEY_CUSTOM -> readCustom(reader)
            else -> null
        } ?: return null
        return SudokuPuzzleRef(GameId(id.decodeToString()), key)
    }

    private fun readDaily(reader: Reader): PuzzleKey.Daily? {
        val epochDay = reader.i64()?.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() } ?: return null
        val number = when (reader.u8()) {
            0 -> null
            1 -> reader.i32() ?: return null
            else -> return null
        }
        return PuzzleKey.Daily(LocalDate.ofEpochDay(epochDay), number)
    }

    /** Null on truncation, an empty id, or bytes that are not UTF-8. */
    private fun readCustom(reader: Reader): PuzzleKey.Custom? {
        val len = reader.u8()?.takeIf { it > 0 } ?: return null
        val code = reader.bytes(len) ?: return null
        return runCatching { PuzzleKey.Custom(code.decodeToString(throwOnInvalidSequence = true)) }.getOrNull()
    }

    /** Null when the cell is malformed or disagrees with the puzzle about which cells are given. */
    private fun readCell(reader: Reader, puzzle: SudokuPuzzle, idx: Int): SudokuCell? {
        val cell = SudokuCommandCodec.readCell(reader, puzzle.geometry.size) ?: return null
        val given = puzzle.givens[idx]
        val consistent = if (given == 0) cell !is SudokuCell.Given else cell == SudokuCell.Given(given)
        return cell.takeIf { consistent }
    }

    private fun writeHistory(out: ByteArrayOutputStream, commands: List<SudokuCommand>) {
        // Dropping the far end only shortens how far undo or redo reaches; the board is untouched.
        val kept = commands.takeLast(MAX_HISTORY)
        out.writeLe(kept.size.toLong(), bytes = 2)
        kept.forEach { SudokuCommandCodec.write(out, it) }
    }

    private fun readHistory(reader: Reader, puzzle: SudokuPuzzle, stack: String): DecodeResult<List<SudokuCommand>> {
        val count = reader.u16() ?: return malformed("truncated $stack count")
        if (count > MAX_HISTORY) return malformed("$stack history of $count commands exceeds $MAX_HISTORY")
        val commands = ArrayList<SudokuCommand>(count)
        repeat(count) {
            commands += SudokuCommandCodec.read(reader, puzzle) ?: return malformed("malformed $stack command $it")
        }
        return DecodeResult.Ok(commands)
    }

    /**
     * True when every undo from [board] and every redo after it is the exact inverse step it
     * claims, so a restored session can never undo into a board its commands were not built on.
     */
    private fun walksFrom(board: SudokuBoard, history: CommandHistory<SudokuBoard, SudokuCommand>): Boolean {
        var state = board
        for (command in history.done.asReversed()) {
            if (!command.fitsOpenCell(state)) return false
            val previous = command.revert(state)
            if (command.apply(previous) != state) return false
            state = previous
        }
        state = board
        for (command in history.undone.asReversed()) {
            if (!command.fitsOpenCell(state)) return false
            val next = command.apply(state)
            if (command.revert(next) != state) return false
            state = next
        }
        return true
    }

    /** A note toggle is the one command that throws on a filled cell; every other kind is total. */
    private fun SudokuCommand.fitsOpenCell(state: SudokuBoard): Boolean =
        this !is SudokuCommand.ToggleNote || state.cells[idx].let { it == SudokuCell.Empty || it is SudokuCell.Notes }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
