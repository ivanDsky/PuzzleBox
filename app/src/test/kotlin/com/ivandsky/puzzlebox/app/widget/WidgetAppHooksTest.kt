package com.ivandsky.puzzlebox.app.widget

import android.app.Activity
import com.ivandsky.puzzlebox.app.DrainTestApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The start hook: the component enable, one redraw per change burst, one per activity start, the rollover alarm. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WidgetAppHooksTest {

    private val changes = MutableSharedFlow<Unit>()
    private var refreshes = 0
    private var refreshFailure: Exception? = null
    private val refresher = WidgetRefresher {
        refreshes++
        refreshFailure?.let { throw it }
    }
    private val alarm = FakeWidgetRolloverAlarm()
    private val armRollover = ArmWidgetRollover(
        zones = { listOf(ZoneId.of("America/Los_Angeles")) },
        hasWidgets = { true },
        alarm = alarm,
        clock = Clock.fixed(Instant.parse("2026-11-20T23:00:00Z"), ZoneOffset.UTC),
    )

    private fun TestScope.hooks(switch: WidgetComponentSwitch = FakeWidgetComponentSwitch()) = WidgetAppHooks(
        componentSwitch = switch,
        changes = changes,
        refresher = refresher,
        armRollover = armRollover,
        application = RuntimeEnvironment.getApplication(),
        scope = backgroundScope,
    ).also {
        it.start()
        runCurrent()
    }

    private fun startActivity() {
        Robolectric.buildActivity(Activity::class.java).create().start()
    }

    @Test
    fun `the start enables the component`() = runTest {
        val switch = FakeWidgetComponentSwitch()

        hooks(switch)

        assertEquals(1, switch.enables)
    }

    @Test
    fun `the start arms the rollover and never cancels it`() = runTest {
        hooks()

        assertEquals(listOf(ROLLOVER), alarm.scheduled)
        assertEquals(0, alarm.cancels)
    }

    @Test
    fun `each activity start arms the rollover once more`() = runTest {
        hooks()

        startActivity()
        runCurrent()

        assertEquals(listOf(ROLLOVER, ROLLOVER), alarm.scheduled)
    }

    @Test
    fun `a switch failure does not stop the redraws`() = runTest {
        hooks(FakeWidgetComponentSwitch(IllegalStateException("user locked")))

        changes.emit(Unit)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, refreshes)
    }

    @Test
    fun `one change gives one redraw after the debounce`() = runTest {
        hooks()

        changes.emit(Unit)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, refreshes)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, refreshes)
    }

    @Test
    fun `a burst of changes inside the window gives one redraw`() = runTest {
        hooks()

        repeat(5) {
            changes.emit(Unit)
            advanceTimeBy(200)
        }
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, refreshes)
    }

    @Test
    fun `a throwing redraw is swallowed and a later change still redraws`() = runTest {
        hooks()
        refreshFailure = IllegalStateException("glance gone")

        changes.emit(Unit)
        advanceTimeBy(1_000)
        runCurrent()
        refreshFailure = null
        changes.emit(Unit)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(2, refreshes)
    }

    @Test
    fun `an activity start redraws once`() = runTest {
        hooks()

        startActivity()
        runCurrent()

        assertEquals(1, refreshes)
    }

    private companion object {
        val ROLLOVER: Instant = Instant.parse("2026-11-21T08:00:00Z")
    }
}
