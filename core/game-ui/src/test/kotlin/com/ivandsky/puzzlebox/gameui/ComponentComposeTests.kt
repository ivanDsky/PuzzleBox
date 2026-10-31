package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText("01:05").assertExists()
    }

    @Test
    fun `zero elapsed renders 00_00`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TimerBar(elapsedMs = 0, modifier = Modifier.requiredSize(width = 200.dp, height = 40.dp))
            }
        }
        composeRule.onNodeWithText("00:00").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WinOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(visible: Boolean, isRecord: Boolean? = null) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                WinOverlay(
                    visible = visible,
                    timeMs = 95_000,
                    hintsUsed = 1,
                    isRecord = isRecord,
                    onReplay = {},
                    onClose = {},
                    modifier = Modifier.requiredSize(300.dp),
                )
            }
        }
    }

    @Test
    fun `shows normalized a3 facts when visible`() {
        setContent(visible = true)
        composeRule.onNodeWithText("Solved!").assertExists()
        composeRule.onNodeWithText("01:35").assertExists()
        composeRule.onNodeWithText("1 hint").assertExists()
    }

    @Test
    fun `record badge appears when stamped`() {
        setContent(visible = true, isRecord = true)
        composeRule.onNodeWithText("New personal record").assertExists()
    }

    @Test
    fun `record badge absent while the write is still in flight`() {
        setContent(visible = true, isRecord = null)
        composeRule.onNodeWithText("New personal record").assertDoesNotExist()
    }

    @Test
    fun `hidden overlay renders nothing`() {
        setContent(visible = false)
        composeRule.onNodeWithText("Solved!").assertDoesNotExist()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HintButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `hint presses reach the game and count displays`() {
        var pressed = 0
        val hints = HintController()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HintButton(hints = hints, onHint = {
                    hints.recordHint()
                    pressed++
                })
            }
        }
        composeRule.onNodeWithText("Hint").performClick()
        composeRule.waitForIdle()
        assertEquals(1, pressed)
        assertEquals(1, hints.usedCount)
        composeRule.onNodeWithText("Hint · 1").assertExists()
    }

    @Test
    fun `disabled button does not fire`() {
        var pressed = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HintButton(hints = HintController(), enabled = false, onHint = { pressed++ })
            }
        }
        composeRule.onNodeWithText("Hint").performClick()
        composeRule.waitForIdle()
        assertFalse(pressed > 0)
    }
}
