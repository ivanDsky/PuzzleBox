package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec A11: the play chrome's "How to play" button reopens the tutorial, and only when a game offers one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayChromeHowToPlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setChrome(onHowToPlay: (() -> Unit)?) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayChrome(
                    title = "Tango",
                    subtitle = "Daily",
                    clock = null,
                    hints = null,
                    undo = null,
                    banner = null,
                    onHowToPlay = onHowToPlay,
                )
            }
        }
    }

    @Test
    fun `no tutorial means no how to play button`() {
        setChrome(onHowToPlay = null)

        composeRule.onNodeWithContentDescription("How to play").assertDoesNotExist()
    }

    @Test
    fun `how to play is a full-size button that opens the tutorial`() {
        var opened = 0
        setChrome(onHowToPlay = { opened++ })

        composeRule.onNodeWithContentDescription("How to play")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle { assertEquals(1, opened) }
    }
}
