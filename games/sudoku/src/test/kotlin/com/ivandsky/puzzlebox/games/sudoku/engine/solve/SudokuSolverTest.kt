package com.ivandsky.puzzlebox.games.sudoku.engine.solve

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuSolverTest {

    private fun solve(puzzle: SudokuPuzzle, limit: Int = 2) = SudokuSolver.solve(puzzle.geometry, puzzle.givens, limit)

    private fun SudokuPuzzle.withoutGivenAt(idx: Int): List<Int> = givens.toMutableList().also { it[idx] = 0 }

    @Test
    fun `each published board is unique and solves to its published solution`() {
        for (puzzle in listOf(KnownSolutions.peanut450, KnownSolutions.miniSample, KnownSolutions.classicSample)) {
            assertEquals(SolveResult(1, puzzle.solution), solve(puzzle))
        }
    }

    @Test
    fun `dropping the r3c3 given of the classic sample makes it ambiguous`() {
        val puzzle = KnownSolutions.classicSample
        val r3c3 = 2 * 9 + 2
        assertEquals(8, puzzle.givens[r3c3])
        assertTrue(SudokuSolver.solve(puzzle.geometry, puzzle.withoutGivenAt(r3c3)).count >= 2)
    }

    @Test
    fun `dropping the r1c3 given of #450 makes it ambiguous`() {
        val puzzle = KnownSolutions.peanut450
        assertEquals(3, puzzle.givens[2])
        assertEquals(2, SudokuSolver.solve(puzzle.geometry, puzzle.withoutGivenAt(2)).count)
    }

    @Test
    fun `the count stops at the limit`() {
        val empty = List(SudokuVariants.MINI.geometry.cellCount) { 0 }
        assertEquals(1, SudokuSolver.solve(SudokuVariants.MINI.geometry, empty, limit = 1).count)
        assertEquals(2, SudokuSolver.solve(SudokuVariants.MINI.geometry, empty).count)
        assertEquals(50, SudokuSolver.solve(SudokuVariants.CLASSIC.geometry, List(81) { 0 }, limit = 50).count)
    }

    @Test
    fun `a limited solve still reports the first solution it found`() {
        val result = solve(KnownSolutions.classicSample, limit = 1)
        assertEquals(SolveResult(1, KnownSolutions.classicSample.solution), result)
    }

    @Test
    fun `givens that clash have no solution`() {
        val geometry = SudokuVariants.MINI.geometry
        val clashInRow = List(geometry.cellCount) { if (it == 0 || it == 5) 3 else 0 }
        assertEquals(SolveResult(0, null), SudokuSolver.solve(geometry, clashInRow))
    }

    @Test
    fun `givens that leave a cell without candidates have no solution`() {
        // Row 0 holds 1..5, column 5 holds 6 below it: cell (0,5) can take nothing.
        val geometry = SudokuVariants.MINI.geometry
        val givens = MutableList(geometry.cellCount) { 0 }
        for (col in 0 until 5) givens[col] = col + 1
        givens[4 * 6 + 5] = 6
        val result = SudokuSolver.solve(geometry, givens)
        assertEquals(0, result.count)
        assertNull(result.solution)
    }

    @Test
    fun `a full valid board is its own single solution`() {
        val puzzle = KnownSolutions.classicSample
        assertEquals(SolveResult(1, puzzle.solution), SudokuSolver.solve(puzzle.geometry, puzzle.solution))
    }
}
