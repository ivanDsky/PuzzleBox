package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A day has to stay on screen for half a second before LinkedIn is asked about it, so scrubbing
 * through days asks nothing; and a day that leaves takes its wait, or its running sync, with it.
 * All time here is the test scheduler's.
 */
class SyncLinkedInDayTest {

    private val zip = GameId("zip")
    private val firstDay = LocalDate.of(2026, 11, 2)

    private val days = FakeLinkedInDays()
    private val sync = SyncLinkedInDay(days)

    @Test
    fun `five days scrubbed inside 500 ms make no sync`() = runTest {
        val last = scrubFiveDays()
        advanceTimeBy(100)
        last.cancel()

        assertEquals(500, testScheduler.currentTime)
        settle()
        assertTrue(days.syncCalls.isEmpty())
    }

    @Test
    fun `a day left inside 500 ms makes no sync`() = runTest {
        val shown = launch { sync(zip, firstDay) }
        advanceTimeBy(499)
        runCurrent()
        shown.cancel()

        settle()
        assertTrue(days.syncCalls.isEmpty())
    }

    @Test
    fun `a day that stays for 500 ms syncs exactly once`() = runTest {
        days.syncResult = LinkedInSync.Paused(LinkedInRead.Rejected.Kind.SessionExpired)

        val shown = async { sync(zip, firstDay) }
        advanceTimeBy(499)
        runCurrent()
        assertTrue(days.syncCalls.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(zip to firstDay), days.syncCalls)
        assertEquals(LinkedInSync.Paused(LinkedInRead.Rejected.Kind.SessionExpired), shown.await())

        settle()
        assertEquals(1, days.syncCalls.size)
    }

    @Test
    fun `the last day of a scrub syncs`() = runTest {
        scrubFiveDays()
        val lastStartedAtMs = testScheduler.currentTime

        advanceTimeBy(499)
        runCurrent()
        assertTrue(days.syncCalls.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(lastStartedAtMs + 500, testScheduler.currentTime)
        assertEquals(listOf(zip to firstDay.plusDays(4)), days.syncCalls)

        settle()
        assertEquals(1, days.syncCalls.size)
    }

    @Test
    fun `cancelling mid-sync cancels the sync`() = runTest {
        val gate = CompletableDeferred<Unit>()
        days.syncGate = gate
        var result: LinkedInSync? = null

        val shown = launch { result = sync(zip, firstDay) }
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(zip to firstDay), days.syncCalls)
        assertTrue(shown.isActive)

        shown.cancel()
        runCurrent()
        assertTrue(shown.isCancelled)
        assertTrue(shown.isCompleted)

        // Letting the abandoned call through later must not bring an answer back or start another.
        gate.complete(Unit)
        settle()
        assertNull(result)
        assertEquals(1, days.syncCalls.size)
    }

    /**
     * Shows five consecutive days 100 ms apart, each one replacing (cancelling) the day before it,
     * and returns the last day's job just after it started.
     */
    private fun TestScope.scrubFiveDays(): Job {
        var shown = launch { sync(zip, firstDay) }
        runCurrent()
        for (n in 1L..4L) {
            advanceTimeBy(100)
            runCurrent()
            shown.cancel()
            shown = launch { sync(zip, firstDay.plusDays(n)) }
            runCurrent()
        }
        return shown
    }

    /** Long enough for any wait that survived, and any retry, to have fired. */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }
}
