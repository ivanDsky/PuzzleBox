package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.requiredSize
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TimerBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders elapsed time counting up`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TimerBar(elapsedMs = 65_000, modifier = Modifier.requiredSize(width = 200.dp, height = 40.dp))
            }
        }
        composeRule.onNodeWithText("1:05").assertExists()
    }

    @Test
    fun `zero elapsed renders 0_00`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TimerBar(elapsedMs = 0, modifier = Modifier.requiredSize(width = 200.dp, height = 40.dp))
            }
        }
        composeRule.onNodeWithText("0:00").assertExists()
    }
}
