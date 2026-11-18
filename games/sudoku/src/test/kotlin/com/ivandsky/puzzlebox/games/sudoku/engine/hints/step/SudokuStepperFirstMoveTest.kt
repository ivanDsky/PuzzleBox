package com.ivandsky.puzzlebox.games.sudoku.engine.hints.step

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The first-move rule: a single the player sees at a glance, one in a unit with at most two
 * empty cells, is skipped for the first forced single that is not obvious. Mistakes still come first.
 */
class SudokuStepperFirstMoveTest {

    /** A mini solution: 612345 345612 456123 123456 561234 234561. */
    private val solution = KnownSolutions.grid("612345 345612 456123 123456 561234 234561")

    private fun puzzle(givens: Map<Int, Int>): SudokuPuzzle =
        SudokuPuzzle(SudokuVariants.MINI, List(36) { givens[it] ?: 0 }, solution)

    private fun next(puzzle: SudokuPuzzle, board: SudokuBoard = SudokuBoard.of(puzzle)) =
        SudokuStepper.next(puzzle, board, lastMove = null, known = emptyMap())

    private fun single(cell: Int, digit: Int, technique: SudokuTechnique, unit: SudokuUnit, lastCell: SudokuUnit?, blockers: Set<Int>) =
        SudokuStep.Single(SudokuHint.Place(cell, digit, technique, unit, null, emptyList()), lastCell, blockers)

    // Row 5 holds 2 3 4 5 6 and lacks only its 1 at cell 35: the single anyone sees. The 6s at cells 9, 14 and 25
    // leave cell 0 the only home for 6 in box 0, which is still empty: forced, and not obvious. Cell 23 is the same
    // for box 3 (the 6 at cell 34 closes its column) at the same cost, so the lower cell wins.
    private val lastCellAndOpenBox = mapOf(30 to 2, 31 to 3, 32 to 4, 33 to 5, 34 to 6, 9 to 6, 14 to 6, 25 to 6)

    private val sixInOpenBox = single(0, 6, SudokuTechnique.HIDDEN_SINGLE_BOX, SudokuUnit(UnitKind.BOX, 0), null, setOf(9, 14, 25))

    private val row5 = SudokuUnit(UnitKind.ROW, 5)

    @Test
    fun `the forced single in a wide open box is chosen over a row's last cell`() {
        assertEquals(sixInOpenBox, next(puzzle(lastCellAndOpenBox)))
    }

    @Test
    fun `a wrong entry still comes before the first move`() {
        val puzzle = puzzle(lastCellAndOpenBox)
        // Cell 1 is 1 in the solution; no peer holds a 5.
        val board = SudokuBoard.of(puzzle).with(1, SudokuCell.Entry(5))
        assertEquals(SudokuStep.Mistake(1, 5, null), next(puzzle, board))
    }

    @Test
    fun `a cell that is obvious as a last cell is not offered again as a cheaper box single`() {
        // The 1 at cell 26 also makes cell 35 the only home for 1 in box 5, which has four empty cells: one blocker,
        // cheaper than the three of cell 0. It is the same cell the row gives away, so it stays skipped.
        // (Cell 24 becomes box 4's last cell, obvious as well.)
        assertEquals(sixInOpenBox, next(puzzle(lastCellAndOpenBox + (26 to 1))))
    }

    @Test
    fun `when every single is obvious, the usual pick stands and no elimination or reveal jumps ahead`() {
        // Column 0 has two empty cells: the 6 at cell 9 leaves cell 0 its only home for 6, and cell 6 can only be 3.
        val puzzle = puzzle(mapOf(9 to 6, 12 to 4, 18 to 1, 24 to 5, 30 to 2))
        assertEquals(0, (next(puzzle) as SudokuStep.Single).place.cell)
    }
}
