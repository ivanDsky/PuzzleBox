package com.ivandsky.puzzlebox.app.widget

import android.util.Log
import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ArmWidgetRolloverTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val alarm = FakeWidgetRolloverAlarm()
    // 19:00 in Tokyo and 02:00 in LA: Tokyo's midnight is the nearer one.
    private val now = Instant.parse("2026-11-21T10:00:00Z")

    private fun arm(zones: () -> Collection<ZoneId>, alarm: WidgetRolloverAlarm = this.alarm) =
        ArmWidgetRollover(zones, alarm, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `schedules the earliest next midnight across the zones`() {
        arm({ listOf(la, tokyo) })()

        assertEquals(listOf(Instant.parse("2026-11-21T15:00:00Z")), alarm.scheduled)
    }

    @Test
    fun `no zones schedules nothing`() {
        arm({ emptyList() })()

        assertTrue(alarm.scheduled.isEmpty())
    }

    @Test
    fun `a second arm schedules again, to replace the first`() {
        val arm = arm({ listOf(la) })

        arm()
        arm()

        assertEquals(2, alarm.scheduled.size)
        assertEquals(alarm.scheduled[0], alarm.scheduled[1])
    }

    @Test
    fun `a failing alarm is swallowed to a log line`() {
        arm({ listOf(la) }, FakeWidgetRolloverAlarm(IllegalStateException("alarm service gone")))()

        assertTrue(ShadowLog.getLogsForTag("ArmWidgetRollover").any { it.type == Log.WARN })
    }
}
