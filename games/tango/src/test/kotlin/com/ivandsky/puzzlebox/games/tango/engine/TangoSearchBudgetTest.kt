package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bounded search returns up to `limit` distinct solutions, polls its [Budget] at every
 * node and answers [TangoSearchOutcome.Exhausted] once it is spent; the most open 6×6 board
 * settles inside the editor's 1.5 s bound.
 */
class TangoSearchBudgetTest {

    @Test
    fun `a two-solution board returns both solutions`() {
        val outcome = TangoUniqueness.bounded(twoSolutions, limit = 2, budget = Budget.Unlimited)

        val solutions = (outcome as TangoSearchOutcome.Done).solutions
        assertEquals(2, solutions.size)
        assertEquals(setOf(grid("SMSM", "MSMS", "SSMM", "MMSS"), grid("SMSM", "MSMS", "SMSM", "MSMS")), solutions.toSet())
        solutions.forEach { assertTrue(TangoRules.isComplete(TangoState(twoSolutions, it))) }
    }

    @Test
    fun `the limit caps how many solutions are returned`() {
        val outcome = TangoUniqueness.bounded(twoSolutions, limit = 1, budget = Budget.Unlimited)

        assertEquals(1, (outcome as TangoSearchOutcome.Done).solutions.size)
    }

    @Test
    fun `an unlimited budget keeps the sample's unique solution`() {
        val outcome = TangoUniqueness.bounded(TangoTestSupport.sample, limit = 2, budget = Budget.Unlimited)

        assertEquals(TangoSearchOutcome.Done(listOf(TangoTestSupport.solution)), outcome)
    }

    @Test
    fun `a cancelled budget is Exhausted at the first poll`() {
        var polls = 0
        val cancelled = Budget { polls++; true }

        val outcome = TangoUniqueness.bounded(emptySix, limit = 2, budget = cancelled)

        assertEquals(TangoSearchOutcome.Exhausted, outcome)
        assertEquals(1, polls)
    }

    @Test
    fun `a budget spent mid-search stops the search with Exhausted`() {
        var polls = 0
        val spentAfterTen = Budget { ++polls > 10 }

        val outcome = TangoUniqueness.bounded(emptySix, limit = 2, budget = spentAfterTen)

        assertEquals(TangoSearchOutcome.Exhausted, outcome)
        assertEquals(11, polls)
    }

    @Test
    fun `the empty 6x6 board settles within the editor budget`() {
        val start = System.nanoTime()

        val outcome = TangoUniqueness.bounded(emptySix, limit = 2, budget = Budget.within(BUDGET_MS, System::nanoTime))

        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI
        assertTrue("took $elapsedMs ms", elapsedMs <= BUDGET_MS)
        val solutions = (outcome as TangoSearchOutcome.Done).solutions
        assertEquals(2, solutions.toSet().size)
    }

    private fun grid(vararg rows: String): List<TangoCell> =
        rows.flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

    private companion object {
        const val BUDGET_MS = 1_500L
        const val NANOS_PER_MILLI = 1_000_000L

        /** Rows 0–1 given; row 2 pinned to S _ _ M, so it is SSMM or SMSM and row 3 its complement. */
        val twoSolutions = TangoPuzzle(
            variant = TangoTestSupport.variant,
            n = 4,
            givens = mapOf(
                0 to TangoCell.SUN, 1 to TangoCell.MOON, 2 to TangoCell.SUN, 3 to TangoCell.MOON,
                4 to TangoCell.MOON, 5 to TangoCell.SUN, 6 to TangoCell.MOON, 7 to TangoCell.SUN,
                8 to TangoCell.SUN, 11 to TangoCell.MOON,
            ),
            markers = emptyList(),
        )

        /** The worst case for the editor: no givens, no markers. */
        val emptySix = TangoPuzzle(TangoTestSupport.variant, n = 6, givens = emptyMap(), markers = emptyList())
    }
}
