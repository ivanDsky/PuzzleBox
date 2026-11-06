package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tile state (A9): offline stays a tap target. */
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
}
