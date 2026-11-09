package com.ivandsky.puzzlebox.game.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Budget.within] runs out on its clock or on cancel; [Budget.cancelled] answers to cancel alone. */
class BudgetTest {

    private var now = 0L
    private var gaveUp = false
    private val budget = Budget.within(TIMEOUT_MS, nanoTime = { now }, cancelled = { gaveUp })

    @Test
    fun `a fresh budget is neither exhausted nor cancelled`() {
        assertFalse(budget.exhausted())
        assertFalse(budget.cancelled())
    }

    @Test
    fun `the timeout exhausts the budget but does not cancel it`() {
        now = TIMEOUT_MS * NANOS_PER_MILLI

        assertTrue(budget.exhausted())
        assertFalse(budget.cancelled())
    }

    @Test
    fun `a cancel both exhausts and cancels the budget`() {
        gaveUp = true

        assertTrue(budget.exhausted())
        assertTrue(budget.cancelled())
    }

    @Test
    fun `a plain budget is cancelled exactly when it is exhausted`() {
        var spent = false
        val plain = Budget { spent }

        assertFalse(plain.cancelled())
        spent = true
        assertTrue(plain.cancelled())
    }

    private companion object {
        const val TIMEOUT_MS = 1_500L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
