package com.ivandsky.puzzlebox.data.linkedin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rolling window of the call budget, with a mutable [now] standing in for the clock. */
class LinkedInCallBudgetTest {

    private val t0 = 1_800_000_000_000L
    private var now = t0

    private fun acquire(budget: LinkedInCallBudget, times: Int): List<Boolean> = List(times) { budget.tryAcquire() }

    @Test
    fun `six calls pass and the seventh is refused within a minute`() {
        val budget = LinkedInCallBudget(nowMs = { now })

        repeat(6) { call ->
            assertTrue("call ${call + 1}", budget.tryAcquire())
            now += 1_000
        }
        assertFalse(budget.tryAcquire())
    }

    @Test
    fun `a slot frees when the oldest call is exactly one window old`() {
        val budget = LinkedInCallBudget(nowMs = { now })
        acquire(budget, times = 6)

        now = t0 + 60_000 - 1
        assertFalse(budget.tryAcquire())
        now = t0 + 60_000
        assertTrue(budget.tryAcquire())
    }

    @Test
    fun `a refused attempt does not extend the window`() {
        val budget = LinkedInCallBudget(nowMs = { now })
        assertTrue(budget.tryAcquire())
        now = t0 + 10_000
        acquire(budget, times = 5)

        now = t0 + 30_000
        assertFalse(budget.tryAcquire())

        now = t0 + 60_000 - 1
        assertFalse(budget.tryAcquire())
        now = t0 + 60_000
        assertTrue("the first call's slot", budget.tryAcquire())
        assertFalse("the refusal took no slot and the other five are 50 s old", budget.tryAcquire())
    }

    @Test
    fun `a custom limit and window are honoured`() {
        val budget = LinkedInCallBudget(nowMs = { now }, limit = 2, windowMs = 1000)

        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
        now = t0 + 999
        assertFalse(budget.tryAcquire())
        now = t0 + 1000
        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
    }

    @Test
    fun `calls spread over the window are counted by their own age`() {
        val budget = LinkedInCallBudget(nowMs = { now })
        acquire(budget, times = 3)
        now = t0 + 30_000
        acquire(budget, times = 3)

        now = t0 + 59_999
        assertFalse(budget.tryAcquire())

        now = t0 + 60_000
        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertFalse("only the three calls made at the start have aged out", budget.tryAcquire())
    }
}
