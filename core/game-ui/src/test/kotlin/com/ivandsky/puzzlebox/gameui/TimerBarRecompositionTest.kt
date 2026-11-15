package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The clock label redraws when the shown second changes, not on every 200 ms tick of the clock loop. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TimerBarRecompositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The fake clock: the play clock's elapsed time, moved by the test. */
    private var nowMs by mutableStateOf(0L)
    private var labelRedraws = 0

    private fun showLabel() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalTimerLabelComposed provides { labelRedraws++ }) {
                    TimerBar(elapsedMs = { nowMs }, modifier = Modifier.requiredSize(width = 200.dp, height = 40.dp))
                }
            }
        }
    }

    @Test
    fun `a 3 s run in 200 ms ticks redraws the label three times, once per new second`() {
        showLabel()
        composeRule.waitForIdle()
        val firstFrame = labelRedraws
        repeat(15) {
            composeRule.runOnIdle { nowMs += 200L }
            composeRule.waitForIdle()
        }
        assertEquals(3, labelRedraws - firstFrame)
        composeRule.onNodeWithText("0:03").assertExists()
    }

    @Test
    fun `a win that lands between ticks shows the result's seconds`() {
        showLabel()
        composeRule.runOnIdle { nowMs = 3_950L }
        composeRule.waitForIdle()
        val resultMs = 4_010L
        composeRule.runOnIdle { nowMs = resultMs }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(formatDuration(resultMs)).assertExists()
    }
}
