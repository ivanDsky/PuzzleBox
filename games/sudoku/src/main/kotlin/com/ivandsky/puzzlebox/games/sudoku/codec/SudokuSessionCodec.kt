package com.ivandsky.puzzlebox.games.sudoku.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.model.GameId
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Session bytes v1, puzzle embedded by value so an unfinished day survives offline.
 * Multi-byte fields are little-endian:
 *
 * ```
 * 0   u8    formatVersion = 1
 *     u16   puzzleLen; puzzleLen x u8     (SudokuCodec bytes)
 *     u8    flags                         (bit0 completedAt, bit1 ref, bit2 notesMode)
 *     [bit1] u8 gameIdLen; ASCII gameId, i64 epochDay, u8 hasNumber, [hasNumber] i32 puzzleNumber
 *     N² x cell                           (row-major)
 *     [bit0] i64 completedAt epochMilli
 *     i64   elapsedMs
 *     i32   hintsUsed
 *
 * cell = u8 kind, then
 *   0 Empty
 *   1 Given  u8 digit
 *   2 Entry  u8 digit, u16 notes
 *   3 Notes  u16 mask
 * ```
 *
 * The undo history is not stored yet (ticket 09): a restored session starts with an empty one.
 * Decode never throws. Anything truncated, out of range, inconsistent with the puzzle's givens
 * or followed by trailing bytes is [DecodeResult.Err], which the caller treats as a fresh start.
 */
object SudokuSessionCodec : GameSessionCodec<SudokuSession> {

    const val VERSION: Int = 1

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2
    private const val FLAG_NOTES_MODE = 4
    private const val KNOWN_FLAGS = FLAG_COMPLETED or FLAG_REF or FLAG_NOTES_MODE

    private const val CELL_EMPTY = 0
    private const val CELL_GIVEN = 1
    private const val CELL_ENTRY = 2
    private const val CELL_NOTES = 3

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
            val id = ref.gameId.value.toByteArray(Charsets.US_ASCII)
            require(id.size in 1..0xFF) { "gameId must be 1..255 ASCII chars" }
            out.write(id.size)
            out.write(id)
            out.writeLe(ref.date.toEpochDay(), bytes = 8)
            val number = ref.puzzleNumber
            out.write(if (number == null) 0 else 1)
            if (number != null) out.writeLe(number.toLong(), bytes = 4)
        }

        session.board.cells.forEach { out.writeCell(it) }
        session.completedAt?.let { out.writeLe(it.toEpochMilli(), bytes = 8) }
        out.writeLe(session.elapsedMs, bytes = 8)
        out.writeLe(session.hintsUsed.toLong(), bytes = 4)
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<SudokuSession> {
        val reader = Reader(bytes)
        val version = reader.u8() ?: return malformed("empty session blob")
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))

        val puzzleLen = reader.u16() ?: return malformed("truncated puzzle length")
        val puzzleBytes = reader.bytes(puzzleLen) ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = SudokuCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return decoded
        }

        val flags = reader.u8() ?: return malformed("truncated flags")
        if (flags and KNOWN_FLAGS.inv() != 0) return malformed("unknown flag bits in $flags")
        val ref = if (flags and FLAG_REF != 0) readRef(reader) ?: return malformed("malformed ref") else null

        val cells = ArrayList<SudokuCell>(puzzle.geometry.cellCount)
        for (idx in 0 until puzzle.geometry.cellCount) {
            cells += readCell(reader, puzzle, idx) ?: return malformed("malformed cell $idx")
        }

        val completedAt = if (flags and FLAG_COMPLETED != 0) {
            Instant.ofEpochMilli(reader.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }
        val elapsedMs = reader.i64()?.takeIf { it >= 0 } ?: return malformed("missing or negative elapsedMs")
        val hintsUsed = reader.i32()?.takeIf { it >= 0 } ?: return malformed("missing or negative hintsUsed")
        if (!reader.atEnd()) return malformed("trailing bytes after session")

        return DecodeResult.Ok(
            SudokuSession(
                ref = ref,
                puzzle = puzzle,
                board = SudokuBoard(puzzle.geometry, cells),
                completedAt = completedAt,
                elapsedMs = elapsedMs,
                hintsUsed = hintsUsed,
                notesMode = flags and FLAG_NOTES_MODE != 0,
            ),
        )
    }

    private fun readRef(reader: Reader): SudokuPuzzleRef? {
        val idLen = reader.u8()?.takeIf { it > 0 } ?: return null
        val id = reader.bytes(idLen)?.takeIf { raw -> raw.all { it >= 0 } } ?: return null
        val epochDay = reader.i64()?.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() } ?: return null
        val number = when (reader.u8()) {
            0 -> null
            1 -> reader.i32() ?: return null
            else -> return null
        }
        return SudokuPuzzleRef(GameId(id.decodeToString()), LocalDate.ofEpochDay(epochDay), number)
    }

    /** Null when the cell is malformed or disagrees with the puzzle about which cells are given. */
    private fun readCell(reader: Reader, puzzle: SudokuPuzzle, idx: Int): SudokuCell? {
        val size = puzzle.geometry.size
        val fullMask = (1 shl size) - 1
        val given = puzzle.givens[idx]
        val cell = when (reader.u8()) {
            CELL_EMPTY -> SudokuCell.Empty
            CELL_GIVEN -> SudokuCell.Given(reader.u8()?.takeIf { it in 1..size } ?: return null)
            CELL_ENTRY -> {
                val digit = reader.u8()?.takeIf { it in 1..size } ?: return null
                SudokuCell.Entry(digit, reader.u16()?.takeIf { it and fullMask.inv() == 0 } ?: return null)
            }
            CELL_NOTES -> SudokuCell.Notes(reader.u16()?.takeIf { it != 0 && it and fullMask.inv() == 0 } ?: return null)
            else -> return null
        }
        val consistent = if (given == 0) cell !is SudokuCell.Given else cell == SudokuCell.Given(given)
        return cell.takeIf { consistent }
    }

    private fun ByteArrayOutputStream.writeCell(cell: SudokuCell) {
        when (cell) {
            SudokuCell.Empty -> write(CELL_EMPTY)
            is SudokuCell.Given -> {
                write(CELL_GIVEN)
                write(cell.digit)
            }
            is SudokuCell.Entry -> {
                write(CELL_ENTRY)
                write(cell.digit)
                writeLe(cell.notes.toLong(), bytes = 2)
            }
            is SudokuCell.Notes -> {
                write(CELL_NOTES)
                writeLe(cell.mask.toLong(), bytes = 2)
            }
        }
    }

    private fun ByteArrayOutputStream.writeLe(value: Long, bytes: Int) {
        repeat(bytes) { write(((value shr (it * 8)) and 0xFF).toInt()) }
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))

    /** Bounds-checked little-endian reads; null instead of reading past the end. */
    private class Reader(private val bytes: ByteArray) {
        private var pos = 0

        fun atEnd(): Boolean = pos == bytes.size

        fun u8(): Int? = le(1)?.toInt()

        fun u16(): Int? = le(2)?.toInt()

        fun i32(): Int? = le(4)?.toInt()

        fun i64(): Long? = le(8)

        fun bytes(count: Int): ByteArray? {
            if (count > bytes.size - pos) return null
            return bytes.copyOfRange(pos, pos + count).also { pos += count }
        }

        private fun le(count: Int): Long? {
            if (count > bytes.size - pos) return null
            var value = 0L
            repeat(count) { value = value or ((bytes[pos + it].toLong() and 0xFF) shl (it * 8)) }
            pos += count
            return value
        }
    }
}
