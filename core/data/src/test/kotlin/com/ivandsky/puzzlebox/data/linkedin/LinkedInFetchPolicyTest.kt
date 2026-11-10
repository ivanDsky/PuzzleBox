package com.ivandsky.puzzlebox.data.linkedin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every cache window and boundary of the fetch policy, exact to the millisecond: each number here is
 * one LinkedIn's throttle or the owner's rules depend on.
 *
 * Pacific-time midnight and daylight-saving days are not tested here. `delta` comes from the game
 * calendar in the day sync; this policy only reads the number.
 */
class LinkedInFetchPolicyTest {

    private val t0 = 1_800_000_000_000L

    /** Is [item] due at [delta], when its last fetch (if [outcome] is not null) was [age] ms ago? */
    private fun due(item: LinkedInItem, delta: Int, outcome: LinkedInOutcome?, age: Long = 0): Boolean =
        LinkedInFetchPolicy.shouldFetch(item, delta, outcome?.let { LinkedInFetch(t0, it) }, nowMs = t0 + age)

    @Test
    fun `an own result never fetched is due, for any delta`() {
        for (delta in listOf(0, 1, 7, 8, 30)) {
            assertTrue("delta $delta", due(LinkedInItem.Own, delta, outcome = null))
        }
    }

    @Test
    fun `a solved own result is never due again`() {
        for (delta in listOf(0, 5)) {
            assertFalse("delta $delta, age 0", due(LinkedInItem.Own, delta, LinkedInOutcome.Solved, age = 0))
            assertFalse("delta $delta, age 400 d", due(LinkedInItem.Own, delta, LinkedInOutcome.Solved, age = 400 * H24))
        }
    }

    @Test
    fun `a past own result that was not played is due after 24 hours`() {
        for (delta in listOf(1, 30)) {
            assertFalse("delta $delta", due(LinkedInItem.Own, delta, LinkedInOutcome.NotPlayed, age = H24 - 1))
            assertTrue("delta $delta", due(LinkedInItem.Own, delta, LinkedInOutcome.NotPlayed, age = H24))
            assertTrue("delta $delta", due(LinkedInItem.Own, delta, LinkedInOutcome.NotPlayed, age = H24 + 1))
        }
    }

    @Test
    fun `today's own result that was not played is due after 2 minutes`() {
        assertFalse(due(LinkedInItem.Own, delta = 0, LinkedInOutcome.NotPlayed, age = 2 * MIN - 1))
        assertTrue(due(LinkedInItem.Own, delta = 0, LinkedInOutcome.NotPlayed, age = 2 * MIN))
    }

    @Test
    fun `a complete past board is never due again`() {
        for (delta in listOf(1, 7)) {
            assertFalse("delta $delta, age 0", due(LinkedInItem.Board, delta, LinkedInOutcome.Board, age = 0))
            assertFalse("delta $delta, age 30 d", due(LinkedInItem.Board, delta, LinkedInOutcome.Board, age = 30 * H24))
            assertTrue("delta $delta, never fetched", due(LinkedInItem.Board, delta, outcome = null))
        }
    }

    @Test
    fun `a past board read on its own day is due once more`() {
        for (delta in listOf(1, 7)) {
            assertTrue("delta $delta, age 0", due(LinkedInItem.Board, delta, LinkedInOutcome.BoardToday, age = 0))
            assertTrue("delta $delta, age 1 min", due(LinkedInItem.Board, delta, LinkedInOutcome.BoardToday, age = MIN))
            assertTrue("delta $delta, age 30 d", due(LinkedInItem.Board, delta, LinkedInOutcome.BoardToday, age = 30 * H24))
        }
        assertFalse("out of the board window", due(LinkedInItem.Board, delta = 8, LinkedInOutcome.BoardToday, age = 8 * H24))
    }

    @Test
    fun `today's board ignores which board outcome it holds`() {
        for (outcome in listOf(LinkedInOutcome.Board, LinkedInOutcome.BoardToday)) {
            assertFalse("$outcome", due(LinkedInItem.Board, delta = 0, outcome, age = 10 * MIN - 1))
            assertTrue("$outcome", due(LinkedInItem.Board, delta = 0, outcome, age = 10 * MIN))
        }
    }

    @Test
    fun `a board older than 7 days is never fetched`() {
        assertFalse(due(LinkedInItem.Board, delta = 8, outcome = null))
        assertFalse(due(LinkedInItem.Board, delta = 30, outcome = null))
        assertTrue("day 7 is the last day in the window", due(LinkedInItem.Board, delta = 7, outcome = null))
    }

    @Test
    fun `today's board is due after 10 minutes`() {
        assertFalse(due(LinkedInItem.Board, delta = 0, LinkedInOutcome.BoardToday, age = 10 * MIN - 1))
        assertTrue(due(LinkedInItem.Board, delta = 0, LinkedInOutcome.BoardToday, age = 10 * MIN))
        assertTrue("never fetched", due(LinkedInItem.Board, delta = 0, outcome = null))
    }

    @Test
    fun `a negative delta is never due`() {
        assertFalse(due(LinkedInItem.Own, delta = -1, outcome = null))
        assertFalse(due(LinkedInItem.Board, delta = -1, outcome = null))
    }

    @Test
    fun `a fetch time in the future is not due`() {
        assertFalse(due(LinkedInItem.Own, delta = 0, LinkedInOutcome.NotPlayed, age = -1))
        assertFalse(due(LinkedInItem.Own, delta = 0, LinkedInOutcome.NotPlayed, age = -H24))
        assertFalse(due(LinkedInItem.Board, delta = 0, LinkedInOutcome.BoardToday, age = -1))
        assertFalse(due(LinkedInItem.Board, delta = 0, LinkedInOutcome.Board, age = -H24))
    }

    private companion object {
        const val MIN = 60_000L
        const val H24 = 24 * 60 * MIN
    }
}
