package com.ivandsky.puzzlebox.games.sudoku.engine.rules

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuConflictsTest {

    private val mini = KnownSolutions.miniSample
    private val classic = KnownSolutions.classicSample

    private fun empty(puzzle: SudokuPuzzle): SudokuBoard =
        SudokuBoard(puzzle.geometry, List(puzzle.geometry.cellCount) { SudokuCell.Empty })

    private fun SudokuBoard.put(vararg placements: Pair<Int, Int>): SudokuBoard =
        placements.fold(this) { board, (idx, digit) -> board.with(idx, SudokuCell.Entry(digit)) }

    private fun solved(puzzle: SudokuPuzzle): SudokuBoard =
        SudokuBoard(puzzle.geometry, puzzle.solution.map { SudokuCell.Entry(it) })

    @Test
    fun `row duplicate is reported on 6x6 and 9x9`() {
        for (puzzle in listOf(mini, classic)) {
            val size = puzzle.geometry.size
            val board = empty(puzzle).put(size + 0 to 4, size + size - 1 to 4)
            assertEquals(
                listOf(SudokuConflict(SudokuUnit(UnitKind.ROW, 1), 4, listOf(size, 2 * size - 1))),
                SudokuConflicts.find(board),
            )
        }
    }

    @Test
    fun `column duplicate is reported on 6x6 and 9x9`() {
        for (puzzle in listOf(mini, classic)) {
            val size = puzzle.geometry.size
            val bottom = (size - 1) * size + 2
            val board = empty(puzzle).put(2 to 3, bottom to 3)
            assertEquals(
                listOf(SudokuConflict(SudokuUnit(UnitKind.COLUMN, 2), 3, listOf(2, bottom))),
                SudokuConflicts.find(board),
            )
        }
    }

    @Test
    fun `box-only duplicate is reported on 6x6 and 9x9`() {
        for (puzzle in listOf(mini, classic)) {
            val size = puzzle.geometry.size
            // (0,0) and (1,1): different row and column, same first box.
            val board = empty(puzzle).put(0 to 5, size + 1 to 5)
            assertEquals(
                listOf(SudokuConflict(SudokuUnit(UnitKind.BOX, 0), 5, listOf(0, size + 1))),
                SudokuConflicts.find(board),
            )
        }
    }

    @Test
    fun `a duplicate sharing row and box is counted in both units`() {
        for (puzzle in listOf(mini, classic)) {
            val board = empty(puzzle).put(0 to 2, 1 to 2)
            assertEquals(
                listOf(
                    SudokuConflict(SudokuUnit(UnitKind.ROW, 0), 2, listOf(0, 1)),
                    SudokuConflict(SudokuUnit(UnitKind.BOX, 0), 2, listOf(0, 1)),
                ),
                SudokuConflicts.find(board),
            )
            assertEquals(setOf(0, 1), SudokuConflicts.cellsIn(SudokuConflicts.find(board)))
        }
    }

    @Test
    fun `givens count and digits within a unit come out ascending`() {
        val board = SudokuBoard(mini.geometry, List(mini.geometry.cellCount) { SudokuCell.Empty })
            .with(0, SudokuCell.Given(6)).put(1 to 1, 3 to 6, 4 to 1, 5 to 6)
        val rowConflicts = SudokuConflicts.find(board).filter { it.unit.kind == UnitKind.ROW }
        assertEquals(
            listOf(
                SudokuConflict(SudokuUnit(UnitKind.ROW, 0), 1, listOf(1, 4)),
                SudokuConflict(SudokuUnit(UnitKind.ROW, 0), 6, listOf(0, 3, 5)),
            ),
            rowConflicts,
        )
    }

    @Test
    fun `a full valid board is solved on 6x6 and 9x9`() {
        for (puzzle in listOf(mini, classic)) {
            val board = solved(puzzle)
            assertTrue(SudokuConflicts.find(board).isEmpty())
            assertTrue(SudokuConflicts.isSolved(board))
        }
    }

    @Test
    fun `a full board with one swap is not solved`() {
        for (puzzle in listOf(mini, classic)) {
            val board = solved(puzzle)
            val a = board.digitAt(0)
            val b = board.digitAt(1)
            val swapped = board.put(0 to b, 1 to a)
            assertTrue(swapped.isFull)
            assertFalse(SudokuConflicts.find(swapped).isEmpty())
            assertFalse(SudokuConflicts.isSolved(swapped))
        }
    }

    @Test
    fun `an incomplete board without conflicts is not solved`() {
        val board = solved(mini).with(0, SudokuCell.Empty)
        assertTrue(SudokuConflicts.find(board).isEmpty())
        assertFalse(SudokuConflicts.isSolved(board))
    }
}
