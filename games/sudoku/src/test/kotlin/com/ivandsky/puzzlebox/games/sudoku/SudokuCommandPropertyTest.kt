package com.ivandsky.puzzlebox.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * revert(apply(s)) == s for every command [SudokuMoves] builds on boards derived from the known
 * solutions. The boards come from fixed fill patterns, not a generator: each open cell takes the
 * fill at its index modulo the pattern length, so every pattern lands on many unit layouts.
 */
class SudokuCommandPropertyTest {

    private enum class Fill { EMPTY, CORRECT, WRONG, SINGLE_NOTE, PAIR_NOTE, ALL_NOTES, HIDDEN_NOTES }

    private val patterns: List<List<Fill>> = listOf(
        listOf(Fill.EMPTY),
        listOf(Fill.CORRECT),
        listOf(Fill.ALL_NOTES),
        listOf(Fill.EMPTY, Fill.CORRECT),
        listOf(Fill.SINGLE_NOTE, Fill.WRONG, Fill.EMPTY),
        listOf(Fill.PAIR_NOTE, Fill.HIDDEN_NOTES, Fill.SINGLE_NOTE, Fill.EMPTY, Fill.WRONG),
        listOf(Fill.ALL_NOTES, Fill.SINGLE_NOTE, Fill.CORRECT, Fill.WRONG, Fill.PAIR_NOTE, Fill.EMPTY, Fill.HIDDEN_NOTES),
    )

    private val puzzles = listOf(KnownSolutions.peanut450, KnownSolutions.miniSample, KnownSolutions.classicSample)

    private val built = mutableMapOf<String, Int>()
    private var notesEmptiedAndRestored = 0

    @Test
    fun `every command reverts exactly on every patterned board of every known solution`() {
        for (puzzle in puzzles) {
            for (pattern in patterns) {
                val board = patterned(puzzle, pattern)
                checkAll(board)
            }
        }
        for (kind in listOf("Place", "Erase", "ToggleNote", "Clear", "HintReveal")) {
            assertTrue("no $kind command was built", (built[kind] ?: 0) > 0)
        }
        assertTrue("no place emptied a single-note peer", notesEmptiedAndRestored > 0)
    }

    private fun checkAll(board: SudokuBoard) {
        val size = board.geometry.size
        for (idx in board.cells.indices) {
            for (digit in 1..size) {
                SudokuMoves.place(board, idx, digit)?.let { check(board, it); checkPeersCleared(board, it) }
                SudokuMoves.reveal(board, idx, digit)?.let { check(board, it) }
                SudokuMoves.toggleNote(board, idx, digit)?.let { check(board, it) }
            }
            SudokuMoves.erase(board, idx)?.let { check(board, it) }
        }
        SudokuMoves.clear(board)?.let { check(board, it) }
    }

    private fun check(board: SudokuBoard, command: SudokuCommand) {
        built.merge(command::class.simpleName!!, 1, Int::plus)
        val after = command.apply(board)
        assertNotEquals("a built command must change the board: $command", board, after)
        assertEquals("revert(apply) differs for $command", board, command.revert(after))
    }

    private fun checkPeersCleared(board: SudokuBoard, place: SudokuCommand.Place) {
        val bit = 1 shl (place.digit - 1)
        val after = place.apply(board)
        for (peer in board.geometry.peersOf(place.idx)) {
            assertEquals("peer $peer still notes ${place.digit} after $place", 0, after.cells[peer].notesMask and bit)
        }
        if (place.clearedPeers.any { board.cells[it] == SudokuCell.Notes(bit) }) notesEmptiedAndRestored++
    }

    private fun patterned(puzzle: SudokuPuzzle, pattern: List<Fill>): SudokuBoard {
        val size = puzzle.geometry.size
        val allNotes = (1 shl size) - 1
        val cells = puzzle.givens.indices.map { idx ->
            val solved = puzzle.solution[idx]
            if (puzzle.givens[idx] != 0) return@map SudokuCell.Given(puzzle.givens[idx])
            val other = solved % size + 1
            when (pattern[idx % pattern.size]) {
                Fill.EMPTY -> SudokuCell.Empty
                Fill.CORRECT -> SudokuCell.Entry(solved)
                Fill.WRONG -> SudokuCell.Entry(other, 1 shl (solved - 1))
                Fill.SINGLE_NOTE -> SudokuCell.Notes(1 shl (solved - 1))
                Fill.PAIR_NOTE -> SudokuCell.Notes((1 shl (solved - 1)) or (1 shl (other - 1)))
                Fill.ALL_NOTES -> SudokuCell.Notes(allNotes)
                Fill.HIDDEN_NOTES -> SudokuCell.Entry(solved, allNotes)
            }
        }
        return SudokuBoard(puzzle.geometry, cells)
    }
}
