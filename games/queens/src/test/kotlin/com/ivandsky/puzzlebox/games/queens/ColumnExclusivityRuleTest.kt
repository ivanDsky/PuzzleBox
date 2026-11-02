package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rule 2 pins: the column predicate in ColumnExclusivityRule. */
class ColumnExclusivityRuleTest {

    private val n = 4
    private val c = { row: Int, col: Int -> QueensTestSupport.cell(row, col, n) }

    @Test
    fun `N=1 - second queen in the same column is rejected`() {
        val puzzle = QueensTestSupport.puzzle(n) { r, col -> r } // region = row, column stays free
        assertEquals(QueensRuleId.ColumnExclusivity, QueensRules.placementViolatedBy(c(2, 1), setOf(c(0, 1)), puzzle))
    }

    @Test
    fun `N=1 - different column is accepted`() {
        val puzzle = QueensTestSupport.puzzle(n)
        assertNull(QueensRules.placementViolatedBy(c(2, 3), setOf(c(0, 0)), puzzle))
    }

    @Test
    fun `N=2 - second queen in a column is accepted, third is rejected`() {
        val puzzle = QueensTestSupport.puzzle(n, queensPerUnit = 2)
        assertNull(QueensRules.placementViolatedBy(c(3, 0), setOf(c(0, 0)), puzzle))
        assertEquals(
            QueensRuleId.ColumnExclusivity,
            QueensRules.placementViolatedBy(c(3, 0), setOf(c(0, 0), c(1, 0)), puzzle),
        )
    }

    @Test
    fun `verbose scan attributes every queen of an over-full column`() {
        val puzzle = QueensTestSupport.puzzle(n)
        val queens = setOf(c(0, 1), c(2, 1), c(3, 3))
        assertEquals(
            setOf(c(0, 1), c(2, 1)),
            QueensRules.violationsOf(QueensRuleId.ColumnExclusivity, queens, puzzle).map { it.cell }.toSet(),
        )
    }
}
