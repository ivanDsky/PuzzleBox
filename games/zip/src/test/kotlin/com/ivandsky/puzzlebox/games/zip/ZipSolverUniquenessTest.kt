package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exhaustive oracle: uniqueness (I10), solution agreement (A16), and the
 * hand-verifiable board where solver claims do NOT depend on the solver itself.
 */
class ZipSolverUniquenessTest {

    private val solver = ZipSolver()

    /**
     * I10 + A16 on the forced board: exactly one full path exists by construction
     * (walls turn every row into a one-way corridor), the solver agrees, and it returns
     * THE path. Deleting the wall filter in the solver yields the alternative
     * 0,5,6,1,... and this test fails — mutation falsification for the wall check.
     */
    @Test
    fun `forced board has exactly one solution and the solver finds it`() {
        val puzzle = ZipTestSupport.n5Puzzle()
        assertEquals(1, solver.countSolutions(puzzle))
        assertEquals(ZipTestSupport.n5Solution, solver.solve(puzzle))
    }

    /**
     * Mutation falsification, other direction: on the open board (same corridors with no
     * walls) many paths exist — an over-pruned solver that wrongly reports uniqueness
     * fails here. Uniqueness claims require exhaustive search, not luck.
     */
    @Test
    fun `open board yields multiple solutions - no false uniqueness`() {
        val open = ZipTestSupport.n5OpenPuzzle()
        assertEquals(2, solver.countSolutions(open, limit = 2))
        assertTrue(solver.solve(open)!!.first() == Cell(0))
    }

    /** Prefix search agrees with the full search on the forced board. */
    @Test
    fun `prefix search completes the forced board exactly`() {
        val puzzle = ZipTestSupport.n5Puzzle()
        val prefix = ZipTestSupport.n5Solution.take(10)
        assertTrue(solver.canComplete(puzzle, prefix))
        assertEquals(ZipTestSupport.n5Solution, solver.completionFrom(puzzle, prefix))
        val deadVariant = ZipTestSupport.path(0, 1, 2, 3, 4, 9, 8, 7, 12)
        // 7->12 crosses the DOWN wall at 7: not a valid trace, rejected before any search
        assertTrue(!solver.canComplete(puzzle, deadVariant))
    }

    /** The shipped-solution gate from the A16 recipe, decoder-free on the in-repo fixture bytes. */
    @Test
    fun `invalid prefixes are rejected before search`() {
        val puzzle = ZipTestSupport.n5Puzzle()
        assertTrue(!solver.canComplete(puzzle, listOf(Cell(0), Cell(5)))) // crosses a wall
        assertTrue(!solver.canComplete(puzzle, listOf(Cell(3)))) // wrong start
        assertTrue(!solver.canComplete(puzzle, ZipTestSupport.path(0, 1, 2, 2))) // revisit
        assertNull(solver.solve(puzzle.copy(board = puzzle.board.copy(waypoints = emptyList()))))
    }

    /** Determinism: same puzzle, same first solution (hints must be stable). */
    @Test
    fun `solve is deterministic across instances`() {
        val puzzle = ZipTestSupport.n5BranchyPuzzle()
        val first = ZipSolver().solve(puzzle)
        val second = ZipSolver().solve(puzzle)
        assertEquals(first, second)
        assertNotEquals(null, first)
    }

    /**
     * longestSolvablePrefix: monotone binary search on the branchy board — the trace
     * [0,1,2,3,8] is dead but [0,1,2,3] completes, so the boundary sits at index 3.
     */
    @Test
    fun `longest solvable prefix finds the divergence point`() {
        val puzzle = ZipTestSupport.n5BranchyPuzzle()
        val deadTrace = ZipTestSupport.path(0, 1, 2, 3, 8)
        assertEquals(4, solver.longestSolvablePrefix(puzzle, deadTrace))
        assertEquals(0, solver.longestSolvablePrefix(puzzle, listOf(Cell(3)))) // invalid prefix
        assertEquals(
            ZipTestSupport.n5Solution.size,
            solver.longestSolvablePrefix(puzzle, ZipTestSupport.n5Solution),
        )
    }
}
