package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.TodayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tile and chip state (A9): offline stays a tap target; the chip's time column fits an hour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an offline tile is enabled and its tap retries`() {
        var taps = 0
        composeRule.setContent {
            PuzzleBoxTheme { GameTile(previewCardModel(TodayStatus.Offline), onClick = { taps++ }) }
        }
        composeRule.onNodeWithTag("card.tile.mini-sudoku").assertIsEnabled().assertHasClickAction().performClick()
        composeRule.onNodeWithTag(statusMarkTag(TodayStatus.Offline), useUnmergedTree = true).assertExists()
        assertEquals(1, taps)
    }

    @Test
    fun `the chip shows an hour-long time unclipped`() {
        composeRule.setContent {
            PuzzleBoxTheme { DoneChip(previewCardModel(TodayStatus.Solved(3_723_000L)), onClick = {}) }
        }
        val node = composeRule.onNodeWithTag("card.chip_time.mini-sudoku", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        assertFalse(layout.hasVisualOverflow)
        assertFalse(layout.isLineEllipsized(0))
        assertEquals(formatDuration(3_723_000L), node.config[SemanticsProperties.Text].single().text)
    }
}
