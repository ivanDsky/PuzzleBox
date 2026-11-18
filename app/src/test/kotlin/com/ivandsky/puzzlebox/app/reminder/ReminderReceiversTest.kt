package com.ivandsky.puzzlebox.app.reminder

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.domain.ComputeNextReminder
import com.ivandsky.puzzlebox.domain.ComputeReminderMessage
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ReminderSettings
import com.ivandsky.puzzlebox.testing.repository.FakeNotificationAccess
import com.ivandsky.puzzlebox.testing.repository.FakeReminderAlarm
import com.ivandsky.puzzlebox.testing.repository.FakeReminderNotifier
import com.ivandsky.puzzlebox.testing.repository.FakeReminderRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ReminderReceiversTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarm = FakeReminderAlarm()
    private val notifier = FakeReminderNotifier()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val expected = Instant.parse("2026-03-10T19:00:00Z")

    @Before
    fun startGraph() {
        val runner = ReminderRunner(
            repository = FakeReminderRepository(ReminderSettings(enabled = true)),
            access = FakeNotificationAccess(true),
            alarm = alarm,
            notifier = notifier,
            todaySet = { listOf(TodayEntry(GameId("zip"), "Zip", TodayStatus.NotStarted)) },
            computeNext = ComputeNextReminder(),
            computeMessage = ComputeReminderMessage(),
            now = { Instant.parse("2026-03-10T12:00:00Z") },
            zone = { ZoneOffset.UTC },
            scope = scope,
        )
        startKoin { modules(module { single { runner } }) }
    }

    @After
    fun stopGraph() {
        scope.cancel()
        stopKoin()
    }

    @Test
    fun `the alarm receiver posts the reminder and arms the next one, with no pending result to finish`() {
        ReminderAlarmReceiver().onReceive(context, Intent(context, ReminderAlarmReceiver::class.java))

        assertEquals(1, notifier.shown.size)
        assertEquals(expected, alarm.scheduled)
    }

    @Test
    fun `the boot receiver re-arms the alarm without posting`() {
        ReminderRebootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(expected, alarm.scheduled)
        assertEquals(0, notifier.shown.size)
    }

    @Test
    fun `the boot receiver re-arms after an app update too`() {
        ReminderRebootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertEquals(expected, alarm.scheduled)
    }

    @Test
    fun `the boot receiver ignores any other broadcast`() {
        ReminderRebootReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        assertNull(alarm.scheduled)
    }
}
