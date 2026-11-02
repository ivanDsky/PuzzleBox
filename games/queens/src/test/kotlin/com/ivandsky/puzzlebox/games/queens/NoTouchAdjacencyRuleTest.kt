package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rule 4 pins — the A1 critical distinction (research 01 §1.2): only the EIGHT touching
 * cells conflict; distant cells on the same chess diagonal are VALID. Deleting the
 * adjacency predicate in NoTouchAdjacencyRule flips these verdicts.
 */
class NoTouchAdjacencyRuleTest {

    private val n = 4
    private val c = { row: Int, col: Int -> QueensTestSupport.cell(row, col, n) }

    /** Region family with no accidental unit collisions on the probe cells below. */
    private val quiet = QueensTestSupport.puzzle(n) { r, col -> (r * 2 + col) % n }

    @Test
    fun `all eight touching cells are rejected - by the adjacency rule itself`() {
        val rule = com.ivandsky.puzzlebox.games.queens.engine.rules.NoTouchAdjacencyRule()
        val queen = c(1, 1)
        val touching = listOf(
            c(0, 0), c(0, 1), c(0, 2),
            c(1, 0), c(1, 2),
            c(2, 0), c(2, 1), c(2, 2),
        )
        for (t in touching) {
            assertEquals(
                "neighbor $t of queen $queen must violate adjacency",
                true,
                rule.violated(t, setOf(queen), quiet),
            )
        }
        val distant = listOf(c(3, 3), c(3, 0), c(0, 3))
        for (d in distant) {
            assertEquals("distant $d must NOT violate adjacency", false, rule.violated(d, setOf(queen), quiet))
        }
    }

    @Test
    fun `A1 - distant diagonal cells are NOT a conflict - never the full chess diagonal`() {
        assertNull(QueensRules.placementViolatedBy(c(3, 3), setOf(c(0, 0)), quiet))
        assertNull(QueensRules.placementViolatedBy(c(2, 2), setOf(c(0, 0)), quiet))
        assertNull(QueensRules.placementViolatedBy(c(3, 0), setOf(c(0, 3)), quiet))
    }

    @Test
    fun `N=2 - adjacency detection is N-independent - still the touching cells only`() {
        val puzzle = QueensTestSupport.puzzle(n, queensPerUnit = 2) { r, col -> (r * 2 + col) % n }
        assertEquals(QueensRuleId.NoTouchAdjacency, QueensRules.placementViolatedBy(c(1, 1), setOf(c(2, 2)), puzzle))
        assertNull(QueensRules.placementViolatedBy(c(0, 3), setOf(c(2, 2)), puzzle))
    }

    @Test
    fun `verbose scan attributes BOTH queens of each touching pair`() {
        val puzzle = QueensTestSupport.puzzle(n) { r, col -> (r + col) % n }
        val queens = setOf(c(0, 0), c(1, 1), c(3, 2))
        val violations = QueensRules.violationsOf(QueensRuleId.NoTouchAdjacency, queens, puzzle)
        assertEquals(setOf(c(0, 0), c(1, 1)), violations.map { it.cell }.toSet())
        assertEquals(2, violations.size) // each side of the pair, once
    }
}
