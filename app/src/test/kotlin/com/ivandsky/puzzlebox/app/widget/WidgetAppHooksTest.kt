package com.ivandsky.puzzlebox.app.widget

import android.app.Activity
import com.ivandsky.puzzlebox.app.DrainTestApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The start hook: the component switch by the flag, one redraw per change burst, one per activity start. */
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

    private fun TestScope.hooks(enabled: Boolean, switch: WidgetComponentSwitch) = WidgetAppHooks(
        enabled = enabled,
        componentSwitch = switch,
        changes = changes,
        refresher = refresher,
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
    fun `flag on enables the component`() = runTest {
        val switch = FakeWidgetComponentSwitch()

        hooks(enabled = true, switch)

        assertEquals(listOf(true), switch.switched)
    }

    @Test
    fun `flag off disables the component and redraws nothing`() = runTest {
        val switch = FakeWidgetComponentSwitch()
        hooks(enabled = false, switch)

        changes.emit(Unit)
        startActivity()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(listOf(false), switch.switched)
        assertEquals(0, refreshes)
    }

    @Test
    fun `a switch failure does not stop the redraws`() = runTest {
        hooks(enabled = true, FakeWidgetComponentSwitch(IllegalStateException("user locked")))

        changes.emit(Unit)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, refreshes)
    }

    @Test
    fun `one change gives one redraw after the debounce`() = runTest {
        hooks(enabled = true, FakeWidgetComponentSwitch())

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
        hooks(enabled = true, FakeWidgetComponentSwitch())

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
        hooks(enabled = true, FakeWidgetComponentSwitch())
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
        hooks(enabled = true, FakeWidgetComponentSwitch())

        startActivity()
        runCurrent()

        assertEquals(1, refreshes)
    }
}
