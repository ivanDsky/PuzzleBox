package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The action bar's optional side-column extra (Sudoku's Erase): drawn only where the controls
 * stack in a column, between Hint and Clear and sharing their width; a row never shows it, so
 * every other game's bar is unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayActionBarSideColumnExtraTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var extraPresses = 0

    private fun setBar(vertical: Boolean, withExtra: Boolean = true) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPlayControlsVertical provides vertical) {
                    Bar(withExtra)
                }
            }
        }
    }

    @Composable
    private fun Bar(withExtra: Boolean) {
        PlayActionBar(
            onUndo = {},
            onRedo = {},
            onHint = {},
            onClear = {},
            canUndo = true,
            canRedo = true,
            hintEnabled = true,
            accent = AccentSlot.Teal,
            sideColumnExtra = if (withExtra) {
                { modifier ->
                    GameButton({ extraPresses++ }, PuzzleIcons.Erase, "Erase", modifier.testTag("test.extra"), size = GameButtonSize.Tall)
                }
            } else {
                null
            },
        )
    }

    @Test
    fun `a column shows the extra between Hint and Clear at the buttons' width, and it presses`() {
        setBar(vertical = true)

        val hint = composeRule.onNodeWithTag("play.hint").getUnclippedBoundsInRoot()
        val extra = composeRule.onNodeWithTag("test.extra").getUnclippedBoundsInRoot()
        val clear = composeRule.onNodeWithTag("play.clear").getUnclippedBoundsInRoot()
        assertTrue("extra $extra under hint $hint", extra.top >= hint.bottom)
        assertTrue("clear $clear under extra $extra", clear.top >= extra.bottom)
        assertEquals(hint.left.value, extra.left.value, 0.5f)
        assertEquals(hint.right.value, extra.right.value, 0.5f)

        composeRule.onNodeWithTag("test.extra").performClick()
        composeRule.waitForIdle()
        assertEquals(1, extraPresses)
    }

    @Test
    fun `a row never shows the extra`() {
        setBar(vertical = false)

        composeRule.onNodeWithTag("test.extra").assertDoesNotExist()
        composeRule.onNodeWithTag("play.clear").assertExists()
    }

    @Test
    fun `no extra leaves the column as it was`() {
        setBar(vertical = true, withExtra = false)

        composeRule.onNodeWithTag("test.extra").assertDoesNotExist()
        composeRule.onNodeWithTag("play.clear").assertExists()
    }
}
