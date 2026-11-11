package com.ivandsky.puzzlebox.data.linkedin

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rolling window of the call budget, with a mutable [now] standing in for the clock. */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /** A budget whose clock is the test scheduler's, so [LinkedInCallBudget.acquire] waits in virtual time. */
    private fun TestScope.virtualBudget(limit: Int = 6) = LinkedInCallBudget({ t0 + testScheduler.currentTime }, limit = limit)

    @Test
    fun `acquire returns at once while a slot is free`() = runTest {
        val budget = virtualBudget(limit = 2)

        budget.acquire()
        budget.acquire()

        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun `acquire waits for the oldest call to age out of the window`() = runTest {
        val budget = virtualBudget(limit = 2)
        budget.acquire()
        advanceTimeBy(10_000)
        budget.acquire()

        val third = async { budget.acquire() }
        runCurrent()
        advanceTimeBy(49_999)
        runCurrent()
        assertFalse("the first call is 59.999 s old", third.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(third.isCompleted)
        assertEquals(60_000, testScheduler.currentTime)
    }

    @Test
    fun `a cancelled wait takes no slot`() = runTest {
        val budget = virtualBudget(limit = 1)
        budget.acquire()
        val waiting = async { budget.acquire() }
        runCurrent()
        advanceTimeBy(30_000)

        waiting.cancelAndJoin()
        advanceTimeBy(30_000)
        runCurrent()

        assertTrue("the cancelled wait recorded nothing, so the one slot is free again", budget.tryAcquire())
    }

    @Test
    fun `the cap is never exceeded however many callers wait`() = runTest {
        val budget = virtualBudget(limit = 6)
        val grantedAt = mutableListOf<Long>()

        repeat(40) { launch { budget.acquire().also { grantedAt += testScheduler.currentTime } } }
        advanceTimeBy(10 * 60_000)
        runCurrent()

        assertEquals(40, grantedAt.size)
        grantedAt.forEach { start ->
            assertTrue("calls in [$start, ${start + 60_000})", grantedAt.count { it in start until start + 60_000 } <= 6)
        }
    }
}
