package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A14: the shared action bar forwards each press to the game, gates on its state, and hides Clear without a seam. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayActionBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val pressed = mutableListOf<String>()

    private fun setBar(onClear: (() -> Unit)?, canUndo: Boolean = true, hintEnabled: Boolean = true, hintsUsed: Int = 0) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayActionBar(
                    onUndo = { pressed += "undo" },
                    onRedo = { pressed += "redo" },
                    onHint = { pressed += "hint" },
                    onClear = onClear,
                    canUndo = canUndo,
                    canRedo = true,
                    hintEnabled = hintEnabled,
                    accent = AccentSlot.Teal,
                    hintsUsed = hintsUsed,
                )
            }
        }
    }

    @Test
    fun `each press reaches the game`() {
        setBar(onClear = { pressed += "clear" })
        listOf("play.undo", "play.redo", "play.hint", "play.clear").forEach {
            composeRule.onNodeWithTag(it).performClick()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("undo", "redo", "hint", "clear"), pressed)
    }

    @Test
    fun `no clear seam hides clear`() {
        setBar(onClear = null)
        composeRule.onNodeWithTag("play.clear").assertDoesNotExist()
        composeRule.onNodeWithTag("play.undo").assertExists()
    }

    @Test
    fun `disabled controls do not fire`() {
        setBar(onClear = null, canUndo = false, hintEnabled = false)
        composeRule.onNodeWithTag("play.undo").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("play.hint").assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("play.redo").assertIsEnabled()
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), pressed)
    }

    @Test
    fun `used hints show their count`() {
        setBar(onClear = null, hintsUsed = 2)
        composeRule.onNode(hasTestTag("play.hint") and hasText("2", substring = true), useUnmergedTree = false)
            .assertExists()
    }

    @Test
    fun `the four actions share the row equally on a 64dp face`() {
        setBar(onClear = {})
        val tags = listOf("play.undo", "play.redo", "play.hint", "play.clear")
        val widths = tags.map { composeRule.onNodeWithTag(it).assertHeightIsAtLeast(64.dp).getUnclippedBoundsInRoot().width }
        assertTrue("action widths $widths", widths.max() - widths.min() < 1.dp)
    }
}
