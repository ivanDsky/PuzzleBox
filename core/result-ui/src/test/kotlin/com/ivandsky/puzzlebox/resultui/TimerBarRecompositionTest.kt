package com.ivandsky.puzzlebox.resultui

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
import com.ivandsky.puzzlebox.gameui.PlayClock
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

    /** A play clock whose elapsed time the test moves, and which freezes on solve like a game's controller. */
    private class FakeClock : PlayClock {
        var nowMs by mutableStateOf(0L)
        private var frozenMs by mutableStateOf<Long?>(null)

        override val clockWindow: Any? = null
        override fun tick() = Unit
        override fun onBackground() = Unit
        override fun onForeground() = Unit
        override fun displayedElapsedMs(): Long = frozenMs ?: nowMs

        /** The solve: the clock stops at [atMs] and the result it shows is that frozen value. */
        fun solveAt(atMs: Long): Long {
            nowMs = atMs
            frozenMs = atMs
            return displayedElapsedMs()
        }
    }

    private val clock = FakeClock()
    private var labelRedraws = 0

    private fun showLabel() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalTimerLabelComposed provides { labelRedraws++ }) {
                    TimerBar(
                        elapsedMs = { clock.displayedElapsedMs() },
                        modifier = Modifier.requiredSize(width = 200.dp, height = 40.dp),
                    )
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
            composeRule.runOnIdle { clock.nowMs += 200L }
            composeRule.waitForIdle()
        }
        assertEquals(3, labelRedraws - firstFrame)
        composeRule.onNodeWithText("0:03").assertExists()
    }

    @Test
    fun `a win that lands between ticks shows the result's seconds`() {
        showLabel()
        composeRule.runOnIdle { clock.nowMs = 3_950L }
        composeRule.waitForIdle()
        lateinit var result: String
        composeRule.runOnIdle { result = formatDuration(clock.solveAt(4_010L)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(result).assertExists()
    }

    @Test
    fun `the label holds the solved time after the clock keeps running`() {
        showLabel()
        composeRule.runOnIdle { clock.nowMs = 3_950L }
        composeRule.waitForIdle()
        lateinit var result: String
        composeRule.runOnIdle { result = formatDuration(clock.solveAt(4_010L)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { clock.nowMs += 60_000L }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(result).assertExists()
    }
}
