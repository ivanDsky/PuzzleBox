package com.ivandsky.puzzlebox.games.sudoku.codec

import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import java.io.ByteArrayOutputStream

/**
 * One [SudokuCommand] in the session layout, plus the cell encoding commands share with the
 * board. Multi-byte fields are little-endian:
 *
 * ```
 * command = u8 tag, then
 *   0 Place       u8 idx, u8 digit, cell before, u8 peerCount, peerCount x u8 peer   (ascending)
 *   1 Erase       u8 idx, cell before
 *   2 ToggleNote  u8 idx, u8 digit
 *   3 Clear       u8 count, count x (u8 idx, cell before)                           (ascending idx)
 *   4 HintReveal  same fields as Place
 *
 * cell = u8 kind, then
 *   0 Empty
 *   1 Given  u8 digit
 *   2 Entry  u8 digit, u16 notes
 *   3 Notes  u16 mask
 * ```
 *
 * [read] checks a command against the puzzle alone: open cells, digits and masks in range,
 * peers that really are peers. Whether the command fits the board is the session codec's replay.
 */
internal object SudokuCommandCodec {

    private const val PLACE = 0
    private const val ERASE = 1
    private const val TOGGLE_NOTE = 2
    private const val CLEAR = 3
    private const val HINT_REVEAL = 4

    private const val CELL_EMPTY = 0
    private const val CELL_GIVEN = 1
    private const val CELL_ENTRY = 2
    private const val CELL_NOTES = 3

    fun write(out: ByteArrayOutputStream, command: SudokuCommand) {
        when (command) {
            is SudokuCommand.Place -> {
                out.write(PLACE)
                writeDigitEntry(out, command.idx, command.digit, command.before, command.clearedPeers)
            }
            is SudokuCommand.HintReveal -> {
                out.write(HINT_REVEAL)
                writeDigitEntry(out, command.idx, command.digit, command.before, command.clearedPeers)
            }
            is SudokuCommand.Erase -> {
                out.write(ERASE)
                out.write(command.idx)
                writeCell(out, command.before)
            }
            is SudokuCommand.ToggleNote -> {
                out.write(TOGGLE_NOTE)
                out.write(command.idx)
                out.write(command.digit)
            }
            is SudokuCommand.Clear -> {
                out.write(CLEAR)
                out.write(command.before.size)
                for (idx in command.before.keys.sorted()) {
                    out.write(idx)
                    writeCell(out, command.before.getValue(idx))
                }
            }
        }
    }

    /** Null when the command is truncated, of an unknown kind or impossible on [puzzle]. */
    fun read(reader: Reader, puzzle: SudokuPuzzle): SudokuCommand? = when (reader.u8()) {
        PLACE -> readDigitEntry(reader, puzzle, SudokuCommand::Place)
        HINT_REVEAL -> readDigitEntry(reader, puzzle, SudokuCommand::HintReveal)
        ERASE -> readErase(reader, puzzle)
        TOGGLE_NOTE -> readToggleNote(reader, puzzle)
        CLEAR -> readClear(reader, puzzle)
        else -> null
    }

    fun writeCell(out: ByteArrayOutputStream, cell: SudokuCell) {
        when (cell) {
            SudokuCell.Empty -> out.write(CELL_EMPTY)
            is SudokuCell.Given -> {
                out.write(CELL_GIVEN)
                out.write(cell.digit)
            }
            is SudokuCell.Entry -> {
                out.write(CELL_ENTRY)
                out.write(cell.digit)
                out.writeLe(cell.notes.toLong(), bytes = 2)
            }
            is SudokuCell.Notes -> {
                out.write(CELL_NOTES)
                out.writeLe(cell.mask.toLong(), bytes = 2)
            }
        }
    }

    /** Null when the cell is truncated, of an unknown kind, or holds a digit or note outside 1..[size]. */
    fun readCell(reader: Reader, size: Int): SudokuCell? {
        val outsideMask = ((1 shl size) - 1).inv()
        return when (reader.u8()) {
            CELL_EMPTY -> SudokuCell.Empty
            CELL_GIVEN -> SudokuCell.Given(reader.u8()?.takeIf { it in 1..size } ?: return null)
            CELL_ENTRY -> {
                val digit = reader.u8()?.takeIf { it in 1..size } ?: return null
                SudokuCell.Entry(digit, reader.u16()?.takeIf { it and outsideMask == 0 } ?: return null)
            }
            CELL_NOTES -> SudokuCell.Notes(reader.u16()?.takeIf { it != 0 && it and outsideMask == 0 } ?: return null)
            else -> null
        }
    }

    private fun writeDigitEntry(out: ByteArrayOutputStream, idx: Int, digit: Int, before: SudokuCell, peers: List<Int>) {
        out.write(idx)
        out.write(digit)
        writeCell(out, before)
        out.write(peers.size)
        peers.forEach { out.write(it) }
    }

    private fun <C : SudokuCommand> readDigitEntry(
        reader: Reader,
        puzzle: SudokuPuzzle,
        build: (idx: Int, digit: Int, before: SudokuCell, clearedPeers: List<Int>) -> C,
    ): C? {
        val idx = readOpenIdx(reader, puzzle) ?: return null
        val digit = readDigit(reader, puzzle) ?: return null
        val before = readCell(reader, puzzle.geometry.size)?.takeIf { it !is SudokuCell.Given } ?: return null
        val candidates = puzzle.geometry.peersOf(idx)
        val count = reader.u8()?.takeIf { it <= candidates.size } ?: return null
        val peers = ArrayList<Int>(count)
        repeat(count) {
            val peer = reader.u8() ?: return null
            val ascending = peers.isEmpty() || peer > peers.last()
            if (!ascending || candidates.binarySearch(peer) < 0 || puzzle.givens[peer] != 0) return null
            peers += peer
        }
        return build(idx, digit, before, peers)
    }

    private fun readErase(reader: Reader, puzzle: SudokuPuzzle): SudokuCommand.Erase? {
        val idx = readOpenIdx(reader, puzzle) ?: return null
        val before = readCell(reader, puzzle.geometry.size)?.takeIf { it is SudokuCell.Entry || it is SudokuCell.Notes }
            ?: return null
        return SudokuCommand.Erase(idx, before)
    }

    private fun readToggleNote(reader: Reader, puzzle: SudokuPuzzle): SudokuCommand.ToggleNote? {
        val idx = readOpenIdx(reader, puzzle) ?: return null
        return SudokuCommand.ToggleNote(idx, readDigit(reader, puzzle) ?: return null)
    }

    private fun readClear(reader: Reader, puzzle: SudokuPuzzle): SudokuCommand.Clear? {
        val count = reader.u8()?.takeIf { it in 1..puzzle.geometry.cellCount } ?: return null
        val before = LinkedHashMap<Int, SudokuCell>(count)
        var previous = -1
        repeat(count) {
            val idx = readOpenIdx(reader, puzzle)?.takeIf { it > previous } ?: return null
            val cell = readCell(reader, puzzle.geometry.size)?.takeIf { it is SudokuCell.Entry || it is SudokuCell.Notes }
                ?: return null
            before[idx] = cell
            previous = idx
        }
        return SudokuCommand.Clear(before)
    }

    private fun readOpenIdx(reader: Reader, puzzle: SudokuPuzzle): Int? =
        reader.u8()?.takeIf { it < puzzle.givens.size && puzzle.givens[it] == 0 }

    private fun readDigit(reader: Reader, puzzle: SudokuPuzzle): Int? =
        reader.u8()?.takeIf { it in 1..puzzle.geometry.size }

    /** Bounds-checked little-endian reads; null instead of reading past the end. */
    class Reader(private val bytes: ByteArray) {
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

internal fun ByteArrayOutputStream.writeLe(value: Long, bytes: Int) {
    repeat(bytes) { write(((value shr (it * 8)) and 0xFF).toInt()) }
}
