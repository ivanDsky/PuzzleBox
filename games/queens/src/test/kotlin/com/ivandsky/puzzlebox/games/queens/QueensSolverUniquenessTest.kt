package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exhaustive oracle (A13 hard gate): uniqueness claims, solution agreement, and
 * hand-verifiable boards where solver claims do NOT depend on the solver itself.
 */
class QueensSolverUniquenessTest {

    private val solver = QueensSolver()

    /** A13 + agreement on the forced board: exactly one solution exists by construction. */
    @Test
    fun `forced board has exactly one solution and the solver finds it`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(1, solver.countSolutions(puzzle))
        assertEquals(QueensTestSupport.n4Solution, solver.solve(puzzle))
        assertTrue(solver.verifyPlacement(puzzle, QueensTestSupport.n4Solution))
    }

    /**
     * Mutation falsification: on the open board (regions redundant with columns) TWO
     * classic 4-queens placements exist — an over-pruned solver that wrongly reports
     * uniqueness fails here. Uniqueness claims require exhaustive search, not luck.
     */
    @Test
    fun `open board yields multiple solutions - no false uniqueness`() {
        val open = QueensTestSupport.n4OpenPuzzle()
        assertEquals(2, solver.countSolutions(open, limit = 2))
    }

    /** A9: the N=2 synthetic board (two queens per row/column/region) is unique and solved. */
    @Test
    fun `N=2 synthetic board has exactly one solution`() {
        val puzzle = QueensTestSupport.n8N2Puzzle()
        assertTrue(solver.verifyPlacement(puzzle, puzzle.solution!!))
        assertEquals(1, solver.countSolutions(puzzle))
        assertEquals(puzzle.solution, solver.solve(puzzle))
    }

    /** verifyPlacement rejects every violation class with the primitive predicates. */
    @Test
    fun `verifyPlacement rejects wrong counts duplicates and touching queens`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        val sol = QueensTestSupport.n4Solution
        assertFalse(solver.verifyPlacement(puzzle, sol.drop(1))) // too few queens
        // Row 0 overfull: (0,0),(0,1),(0,3) — a raw list duplicate would dedupe through Set.
        assertFalse(solver.verifyPlacement(puzzle, QueensTestSupport.path(0, 1, 3, 8, 14)))
        // Same row twice: (0,1) and (0,3) — row count 2 > N=1.
        assertFalse(solver.verifyPlacement(puzzle, QueensTestSupport.path(1, 3, 8, 14)))
        // Touching diagonally: (1,3) idx7 and (2,2)? idx10 not in solution; use (1,1)&(2,2).
        val touchy = QueensTestSupport.path(5, 10, 2, 15)
        assertFalse(solver.verifyPlacement(puzzle, touchy))
        assertTrue(solver.verifyPlacement(puzzle, sol)) // the real thing still passes
    }

    /** The queens-02 composition seam rejects inconsistent candidates (first violated rule). */
    @Test
    fun `rule seam rejects inconsistent candidates`() {
        val puzzle = QueensTestSupport.n4Puzzle()

        // Row rule fires first in pipeline order: idx 9 shares row 2 with idx 8.
        assertEquals(QueensRuleId.RowExclusivity, QueensRules.placementViolatedBy(Cell(9), setOf(Cell(8)), puzzle))
        // Column rule: idx 6 (col 2) vs placed idx 14 (3,2); row/region/adjacency clear.
        assertEquals(QueensRuleId.ColumnExclusivity, QueensRules.placementViolatedBy(Cell(6), setOf(Cell(14)), puzzle))
        // Region rule: idx 11 is region 1, already held by placed idx 6 (row/col clear).
        assertEquals(QueensRuleId.RegionExclusivity, QueensRules.placementViolatedBy(Cell(11), setOf(Cell(6)), puzzle))
        // Adjacency rule: idx 10 touches placed idx 5 diagonally (row/col/region clear).
        assertEquals(QueensRuleId.NoTouchAdjacency, QueensRules.placementViolatedBy(Cell(10), setOf(Cell(5)), puzzle))
        assertEquals(null, QueensRules.placementViolatedBy(Cell(7), setOf(Cell(14)), puzzle))
        // evaluate() on the full solution is Valid; dropping a queen is Incomplete.
        val sol = QueensTestSupport.n4Solution
        assertEquals(QueensRulesVerdict.Valid, QueensRules.evaluate(puzzle, sol.toSet()))
        assertEquals(QueensRulesVerdict.Incomplete(3, 4), QueensRules.evaluate(puzzle, sol.dropLast(1).toSet()))
    }

    /** Unsolvable boards count zero; solve surfaces null, never a partial answer. */
    @Test
    fun `unsolvable board yields zero solutions`() {
        // Region 3 owns ZERO cells, so its required queen can never be placed —
        // unsolvable by construction, independent of any search detail.
        val regions = listOf(
            0, 0, 0, 0,
            0, 1, 1, 1,
            1, 1, 2, 2,
            1, 2, 2, 2,
        )
        val puzzle = QueensPuzzle(4, regions, 1, null)
        assertEquals(0, solver.countSolutions(puzzle))
        assertNull(solver.solve(puzzle))
    }

    /** Determinism: same puzzle, same first solution (hints must be stable). */
    @Test
    fun `solve is deterministic across instances`() {
        val puzzle = QueensTestSupport.n8N2Puzzle()
        assertEquals(QueensSolver().solve(puzzle), QueensSolver().solve(puzzle))
    }
}
