package com.ivandsky.puzzlebox.app.reminder

import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import com.ivandsky.puzzlebox.domain.ComputeNextReminder
import com.ivandsky.puzzlebox.domain.ComputeReminderMessage
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ReminderMessage
import com.ivandsky.puzzlebox.model.ReminderSettings
import com.ivandsky.puzzlebox.testing.repository.FakeNotificationAccess
import com.ivandsky.puzzlebox.testing.repository.FakeReminderAlarm
import com.ivandsky.puzzlebox.testing.repository.FakeReminderNotifier
import com.ivandsky.puzzlebox.testing.repository.FakeReminderRepository
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRunnerTest {

    private val nowInstant = Instant.parse("2026-03-10T12:00:00Z")
    private val seven = LocalTime.of(19, 0)
    private val todayAtSeven = Instant.parse("2026-03-10T19:00:00Z")
    private val tomorrowAtSeven = Instant.parse("2026-03-11T19:00:00Z")
    private val open = listOf(TodayEntry(GameId("zip"), "Zip", TodayStatus.NotStarted))

    private val repository = FakeReminderRepository(ReminderSettings(enabled = true, time = seven))
    private val access = FakeNotificationAccess(true)
    private val alarm = FakeReminderAlarm()
    private val notifier = FakeReminderNotifier()
    private var zone: ZoneId = ZoneOffset.UTC
    private var read: suspend () -> List<TodayEntry> = { open }
    private var postNotifier: ReminderNotifier = notifier

    private fun TestScope.runner(enabled: Boolean = true, scope: CoroutineScope = this) = ReminderRunner(
        enabled = enabled,
        repository = repository,
        access = access,
        alarm = alarm,
        notifier = postNotifier,
        todaySet = { read() },
        computeNext = ComputeNextReminder(),
        computeMessage = ComputeReminderMessage(),
        now = { nowInstant },
        zone = { zone },
        scope = scope,
    )

    @Test
    fun `a disabled runner touches no seam and still finishes the receiver`() = runTest {
        val runner = runner(enabled = false)
        var done = 0

        runner.start()
        runner.refresh { done++ }
        runner.fire { done++ }
        advanceUntilIdle()

        assertEquals(2, done)
        assertNull(alarm.scheduled)
        assertEquals(0, alarm.cancelCount)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `on and allowed arms the next instant`() = runTest {
        runner().refresh()
        advanceUntilIdle()

        assertEquals(todayAtSeven, alarm.scheduled)
    }

    @Test
    fun `start re-arms when the stored time changes`() = runTest {
        val collecting = Job()
        runner(scope = CoroutineScope(StandardTestDispatcher(testScheduler) + collecting)).start()
        try {
            advanceUntilIdle()
            assertEquals(todayAtSeven, alarm.scheduled)

            repository.setTime(LocalTime.of(8, 0))
            advanceUntilIdle()

            assertEquals(tomorrowAt(8), alarm.scheduled)
        } finally {
            collecting.cancel()
        }
    }

    @Test
    fun `off cancels the pending alarm`() = runTest {
        val runner = runner()
        runner.refresh()
        advanceUntilIdle()
        repository.setEnabled(false)

        runner.refresh()
        advanceUntilIdle()

        assertNull(alarm.scheduled)
    }

    @Test
    fun `notifications not allowed cancels the pending alarm`() = runTest {
        val runner = runner()
        runner.refresh()
        advanceUntilIdle()
        access.allowed = false

        runner.refresh()
        advanceUntilIdle()

        assertNull(alarm.scheduled)
    }

    @Test
    fun `fire posts the open puzzles and arms the next day`() = runTest {
        var done = false

        runner().fire { done = true }
        advanceUntilIdle()

        assertEquals(1, notifier.shown.size)
        assertEquals(todayAtSeven, alarm.scheduled)
        assertTrue(done)
    }

    @Test
    fun `fire with the choice off posts nothing and cancels`() = runTest {
        repository.setEnabled(false)

        runner().fire()
        advanceUntilIdle()

        assertTrue(notifier.shown.isEmpty())
        assertNull(alarm.scheduled)
    }

    @Test
    fun `a solved day posts nothing and still arms the next one`() = runTest {
        read = { listOf(TodayEntry(GameId("zip"), "Zip", TodayStatus.Solved(1L))) }

        runner().fire()
        advanceUntilIdle()

        assertTrue(notifier.shown.isEmpty())
        assertEquals(todayAtSeven, alarm.scheduled)
    }

    @Test
    fun `a day with every puzzle offline posts nothing and still arms the next one`() = runTest {
        read = { listOf(TodayEntry(GameId("zip"), "Zip", TodayStatus.Offline)) }

        runner().fire()
        advanceUntilIdle()

        assertTrue(notifier.shown.isEmpty())
        assertEquals(todayAtSeven, alarm.scheduled)
    }

    @Test
    fun `a read that hangs is given up after 8 seconds and tomorrow is still armed`() = runTest {
        read = { CompletableDeferred<List<TodayEntry>>().await() }
        var done = false

        runner().fire { done = true }
        advanceTimeBy(7_999)
        assertFalse(done)
        advanceTimeBy(2)
        advanceUntilIdle()

        assertTrue(done)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(todayAtSeven, alarm.scheduled)
    }

    @Test
    fun `a read that throws posts nothing and still arms the next one`() = runTest {
        read = { error("store closed") }
        var done = false

        runner().fire { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(todayAtSeven, alarm.scheduled)
    }

    @Test
    fun `a post that throws still arms the next one`() = runTest {
        postNotifier = object : ReminderNotifier {
            override fun show(message: ReminderMessage) = throw SecurityException("no permission")
        }

        runner().fire()
        advanceUntilIdle()

        assertEquals(todayAtSeven, alarm.scheduled)
    }

    @Test
    fun `a cancelled fire still arms the next one and finishes`() = runTest {
        read = { CompletableDeferred<List<TodayEntry>>().await() }
        var done = false
        runner().fire { done = true }
        testScheduler.runCurrent()

        coroutineContext.cancelChildren()
        advanceUntilIdle()

        assertTrue(done)
        assertEquals(todayAtSeven, alarm.scheduled)
    }

    @Test
    fun `a zone change moves the next instant`() = runTest {
        val runner = runner()
        runner.refresh()
        advanceUntilIdle()
        zone = ZoneId.of("America/New_York")

        runner.refresh()
        advanceUntilIdle()

        // 19:00 EDT (UTC-4 on 2026-03-10) is 23:00Z, not 19:00Z.
        assertEquals(Instant.parse("2026-03-10T23:00:00Z"), alarm.scheduled)
    }

    private fun tomorrowAt(hour: Int) = Instant.parse("2026-03-11T%02d:00:00Z".format(hour))
}
