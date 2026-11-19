package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A timer given a target and a label counts toward it, then reads how far over it is. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TimerBarTargetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var nowMs by mutableStateOf(0L)

    private fun showBar(targetMs: Long?) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TimerBar(
                    elapsedMs = { nowMs },
                    modifier = Modifier.requiredSize(width = 240.dp, height = 40.dp),
                    budgetMs = targetMs,
                    label = if (targetMs != null) { over, clock -> if (over) "Over by $clock" else "$clock left" } else null,
                )
            }
        }
    }

    private fun at(ms: Long) {
        composeRule.runOnIdle { nowMs = ms }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500L)
    }

    @Test
    fun `before the target the label reads the time left`() {
        showBar(targetMs = 134_000L)
        at(88_000L)
        composeRule.onNodeWithText("0:46 left").assertExists()
    }

    @Test
    fun `at the target's whole second the label reads over by zero`() {
        showBar(targetMs = 134_000L)
        at(133_000L)
        composeRule.onNodeWithText("0:01 left").assertExists()
        at(134_400L)
        composeRule.onNodeWithText("Over by 0:00").assertExists()
    }

    @Test
    fun `past the target the label reads how far over`() {
        showBar(targetMs = 134_000L)
        at(142_000L)
        composeRule.onNodeWithText("Over by 0:08").assertExists()
    }

    @Test
    fun `with no target the timer is the plain count up`() {
        showBar(targetMs = null)
        at(88_000L)
        composeRule.onNodeWithText("1:28").assertExists()
    }
}
