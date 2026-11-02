package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rule 1 pins (mutation falsification, zip precedent): deleting the row predicate in
 * RowExclusivityRule flips these verdicts and fails the suite.
 */
class RowExclusivityRuleTest {

    private val n = 4
    private val c = { row: Int, col: Int -> QueensTestSupport.cell(row, col, n) }

    @Test
    fun `N=1 - second queen in the same row is rejected with the first queen's cell`() {
        val puzzle = QueensTestSupport.puzzle(n) // region = row
        assertEquals(QueensRuleId.RowExclusivity, QueensRules.placementViolatedBy(c(0, 2), setOf(c(0, 0)), puzzle))
    }

    @Test
    fun `N=1 - queen in a different row is accepted`() {
        val puzzle = QueensTestSupport.puzzle(n)
        assertNull(QueensRules.placementViolatedBy(c(2, 1), setOf(c(0, 0)), puzzle))
    }

    @Test
    fun `N=2 - second queen in a row is accepted, third is rejected`() {
        val puzzle = QueensTestSupport.puzzle(n, queensPerUnit = 2)
        val one = setOf(c(1, 0))
        assertNull(QueensRules.placementViolatedBy(c(1, 3), one, puzzle)) // second in row 1
        assertEquals(QueensRuleId.RowExclusivity, QueensRules.placementViolatedBy(c(1, 3), setOf(c(1, 0), c(1, 2)), puzzle))
    }

    @Test
    fun `verbose scan attributes every queen of an over-full row`() {
        val puzzle = QueensTestSupport.puzzle(n)
        val queens = setOf(c(0, 0), c(0, 2), c(3, 1))
        val violations = QueensRules.violationsOf(QueensRuleId.RowExclusivity, queens, puzzle)
        assertEquals(
            setOf(c(0, 0), c(0, 2)),
            violations.map { it.cell }.toSet(),
        )
        assertEquals(setOf(QueensRuleId.RowExclusivity), violations.map { it.rule }.toSet())
    }
}
