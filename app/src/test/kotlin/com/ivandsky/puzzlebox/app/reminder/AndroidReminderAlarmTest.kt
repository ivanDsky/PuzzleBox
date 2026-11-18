package com.ivandsky.puzzlebox.app.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class AndroidReminderAlarmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val alarm = AndroidReminderAlarm(context)
    private val first = Instant.parse("2026-03-10T19:00:00Z")
    private val second = Instant.parse("2026-03-11T08:00:00Z")

    @Test
    fun `schedule arms one inexact wake-up that fires in idle, at the instant`() {
        alarm.schedule(first)

        val armed = shadowOf(manager).scheduledAlarms.single()
        assertEquals(first.toEpochMilli(), armed.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, armed.type)
        assertTrue(armed.allowWhileIdle)
    }

    @Test
    fun `a second schedule replaces the first rather than adding a second alarm`() {
        alarm.schedule(first)
        alarm.schedule(second)

        assertEquals(second.toEpochMilli(), shadowOf(manager).scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun `cancel removes the pending alarm`() {
        alarm.schedule(first)

        alarm.cancel()

        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `cancel with nothing pending is fine`() {
        alarm.cancel()

        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }
}
