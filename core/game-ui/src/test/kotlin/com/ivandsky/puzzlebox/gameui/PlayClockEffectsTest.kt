package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A14 / SC6: the clock folds when the screen leaves the composition while resumed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayClockEffectsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class CountingClock : PlayClock {
        var backgrounds = 0
        override val clockWindow: Any? = null
        override fun tick() = Unit
        override fun onBackground() {
            backgrounds++
        }
        override fun onForeground() = Unit
        override fun displayedElapsedMs(): Long = 0L
    }

    private class TestOwner(state: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = state }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun disposeAfterShowing(state: Lifecycle.State): CountingClock {
        val clock = CountingClock()
        val owner = TestOwner(state)
        var shown by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown) PlayClockEffects(clock)
            }
        }
        composeRule.waitForIdle()
        shown = false
        composeRule.waitForIdle()
        return clock
    }

    @Test
    fun `disposing a resumed effect folds exactly once`() {
        assertEquals(1, disposeAfterShowing(Lifecycle.State.RESUMED).backgrounds)
    }

    @Test
    fun `disposing a never-resumed effect does not fold`() {
        assertEquals(0, disposeAfterShowing(Lifecycle.State.STARTED).backgrounds)
    }

    @Test
    fun `disposing after a pause does not fold again`() {
        val clock = CountingClock()
        val owner = TestOwner(Lifecycle.State.RESUMED)
        var shown by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown) PlayClockEffects(clock)
            }
        }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        shown = false
        composeRule.waitForIdle()
        assertEquals(1, clock.backgrounds)
    }
}
