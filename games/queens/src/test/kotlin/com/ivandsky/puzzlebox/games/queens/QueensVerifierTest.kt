package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifier pins: QueensVerifier.areNeighbors is the eight-neighbors truth, isSolution is
 * the A13 oracle shape, and evaluate() flags EVERY violation class with correct cell
 * attribution (never just the first).
 */
class QueensVerifierTest {

    private val n = 4
    private val c = { row: Int, col: Int -> QueensTestSupport.cell(row, col, n) }

    @Test
    fun `areNeighbors is exactly the eight touching cells`() {
        val queen = 1 to 1
        for (row in 0 until n) {
            for (col in 0 until n) {
                val dr = kotlin.math.abs(row - queen.first)
                val dc = kotlin.math.abs(col - queen.second)
                val expected = (dr <= 1 && dc <= 1) && (dr + dc > 0)
                assertEquals(
                    "cell ($row,$col) vs queen (1,1)",
                    expected,
                    QueensVerifier.areNeighbors(c(1, 1), c(row, col), n),
                )
            }
        }
    }

    @Test
    fun `isSolution accepts a solver-found placement and rejects a broken one`() {
        val puzzle = QueensTestSupport.puzzle(n)
        val solution = QueensTestSupport.solve(puzzle)!!
        assertTrue(QueensVerifier.isSolution(puzzle, solution))
        // break it: swap one queen into a different column of the same row
        val broken = solution.map { if (it == solution.first()) c(n - 1, 0) else it }.toSet()
        assertFalse(QueensVerifier.isSolution(puzzle, broken))
    }

    @Test
    fun `evaluate flags every violation class with cell attribution`() {
        val puzzle = QueensTestSupport.puzzle(n) { r, col -> (r + col) % n }
        // (0,0) and (0,1): same row AND touching; (0,1) and (2,1): same column; (0,0),(0,1) also share... keep the matrix small and assert the exact per-rule cells.
        val queens = setOf(c(0, 0), c(0, 1), c(2, 1), c(3, 2))
        val verdict = QueensRules.evaluate(puzzle, queens)
        assertTrue(verdict is QueensRulesVerdict.Invalid)
        val byRule = (verdict as QueensRulesVerdict.Invalid).violations.groupBy({ it.rule }, { it.cell })
        assertEquals(setOf(c(0, 0), c(0, 1)), byRule[QueensRuleId.RowExclusivity]?.toSet())
        assertEquals(setOf(c(0, 1), c(2, 1)), byRule[QueensRuleId.ColumnExclusivity]?.toSet())
        // two touching pairs: (0,0)-(0,1) and (2,1)-(3,2) — BOTH queens of each pair
        assertEquals(
            setOf(c(0, 0), c(0, 1), c(2, 1), c(3, 2)),
            byRule[QueensRuleId.NoTouchAdjacency]?.toSet(),
        )
        // region (r+col)%n: (0,1) and (3,2) share region 1
        assertEquals(setOf(c(0, 1), c(3, 2)), byRule[QueensRuleId.RegionExclusivity]?.toSet())
    }

    @Test
    fun `evaluate reports Incomplete with exact counts mid-play`() {
        val puzzle = QueensTestSupport.puzzle(n)
        val verdict = QueensRules.evaluate(puzzle, setOf(c(0, 0)))
        assertEquals(QueensRulesVerdict.Incomplete(placed = 1, required = 4), verdict)
    }

    @Test
    fun `evaluate reports Valid for the complete legal placement`() {
        val puzzle = QueensTestSupport.puzzle(n)
        assertEquals(QueensRulesVerdict.Valid, QueensRules.evaluate(puzzle, QueensTestSupport.solve(puzzle)!!))
    }

    @Test
    fun `unitCounts matches row column region membership`() {
        val puzzle = QueensTestSupport.puzzle(n) { r, col -> (r + col) % n }
        val queens = setOf(c(0, 0), c(1, 2))
        val (rows, cols, regions) = QueensVerifier.unitCounts(puzzle, queens)
        assertEquals(intArrayOf(1, 1, 0, 0).toList(), rows.toList())
        assertEquals(intArrayOf(1, 0, 1, 0).toList(), cols.toList())
        assertEquals(intArrayOf(1, 0, 0, 1).toList(), regions.toList())
    }
}
