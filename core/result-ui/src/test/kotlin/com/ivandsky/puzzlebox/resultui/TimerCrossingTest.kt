package com.ivandsky.puzzlebox.resultui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The one rule the bar, its fill and the tablet time card share for crossing past you's time, on whole seconds. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TimerCrossingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the last millisecond before the target's whole second is still under, one second left`() {
        assertEquals(Crossing(over = false, clock = "0:01"), crossing(elapsedMs = 45_999L, targetMs = 46_000L))
    }

    @Test
    fun `the target's whole second is over by zero, however far into that second`() {
        assertEquals(Crossing(over = true, clock = "0:00"), crossing(elapsedMs = 46_000L, targetMs = 46_000L))
        assertEquals(Crossing(over = true, clock = "0:00"), crossing(elapsedMs = 46_999L, targetMs = 46_000L))
    }

    @Test
    fun `past the target the clock counts how far over in whole seconds`() {
        assertEquals(Crossing(over = true, clock = "0:08"), crossing(elapsedMs = 54_400L, targetMs = 46_000L))
    }

    @Test
    fun `a target with a fraction of a second crosses on its whole second`() {
        assertEquals(Crossing(over = true, clock = "0:00"), crossing(elapsedMs = 46_000L, targetMs = 46_900L))
    }

    @Test
    fun `the crossing label takes the caller's modifier`() {
        composeRule.setContent { CrossingLabel(over = false, modifier = Modifier.testTag("label")) { Text("x") } }

        composeRule.onNodeWithTag("label").assertExists()
    }
}
