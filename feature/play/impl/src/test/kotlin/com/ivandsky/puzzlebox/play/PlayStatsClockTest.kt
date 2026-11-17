package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The panel's timer recomposes once per shown second, not on every 200 ms tick of the shared clock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h1600dp")
class PlayStatsClockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var elapsed by mutableLongStateOf(0L)
    private var timerComposes = 0

    private fun show() {
        val state = PlayStatsState(
            gameName = "Zip",
            solvedToday = 0,
            gamesToday = 2,
            times = PlayTimes.Board(emptyList(), solved = false, order = RankOrder.TimeThenHints, post = null),
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPanelTimerComposed provides { timerComposes++ }) {
                    Column {
                        PlayStatsPanel(state = state, elapsedMs = { elapsed }, signIn = BoardSignIn.Idle, onSignIn = {})
                    }
                }
            }
        }
    }

    @Test
    fun `a second of 200 ms ticks recomposes the timer at most twice`() {
        show()
        composeRule.waitForIdle()
        val firstFrame = timerComposes
        repeat(5) {
            composeRule.runOnIdle { elapsed += 200L }
            composeRule.waitForIdle()
        }
        assertTrue("timer composed ${timerComposes - firstFrame} times", timerComposes - firstFrame <= 2)
        composeRule.onNodeWithTag("play.stats.timer").assertTextEquals("0:01")
    }
}
