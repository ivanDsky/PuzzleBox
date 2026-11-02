package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rule 3 pins: the region predicate in RegionExclusivityRule — including irregular
 * region shapes (research 01 §1.4) and the N=2 allowance.
 */
class RegionExclusivityRuleTest {

    private val n = 4
    private val c = { row: Int, col: Int -> QueensTestSupport.cell(row, col, n) }

    /** Irregular stripe regions: columns 0–1 = region 0, columns 2–3 = region 2. */
    private val stripes = QueensTestSupport.puzzle(n) { _, col -> (col / 2) * 2 }

    @Test
    fun `N=1 - second queen in the same irregular region is rejected`() {
        // irregular region map: (r/2)*2 + col/2 — region 0 spans rows 0-1, cols 0-1;
        // (1,1) shares region 0 with (0,0) and (region precedes adjacency in the pipeline).
        val puzzle = QueensTestSupport.puzzle(n) { r, col -> r / 2 * 2 + col / 2 }
        assertEquals(QueensRuleId.RegionExclusivity, QueensRules.placementViolatedBy(c(1, 1), setOf(c(0, 0)), puzzle))
    }

    @Test
    fun `N=1 - same row band, different region is accepted`() {
        val puzzle = QueensTestSupport.puzzle(n) { _, col -> col }
        assertNull(QueensRules.placementViolatedBy(c(2, 3), setOf(c(0, 0)), puzzle))
    }

    @Test
    fun `N=2 - two queens per region accepted, third rejected`() {
        val puzzle = QueensTestSupport.puzzle(n, queensPerUnit = 2, regionOf = { _, col -> col / 2 })
        assertNull(QueensRules.placementViolatedBy(c(3, 1), setOf(c(0, 0)), puzzle)) // second in region 0
        assertEquals(
            QueensRuleId.RegionExclusivity,
            QueensRules.placementViolatedBy(c(1, 0), setOf(c(0, 0), c(3, 1)), puzzle),
        )
    }

    @Test
    fun `verbose scan attributes every queen of an over-full region`() {
        val queens = setOf(c(0, 0), c(1, 1), c(3, 3)) // (0,0),(1,1) share region 0 in `stripes`
        assertEquals(
            setOf(c(0, 0), c(1, 1)),
            QueensRules.violationsOf(QueensRuleId.RegionExclusivity, queens, stripes).map { it.cell }.toSet(),
        )
    }
}
