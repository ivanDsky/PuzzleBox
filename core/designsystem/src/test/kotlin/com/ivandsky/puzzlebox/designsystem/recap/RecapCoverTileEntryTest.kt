package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A cover tile's entry motion is the caller's to switch off: a tile scrolled into a grid shows its final state at once. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RecapCoverTileEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(entry: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme {
                RecapCoverTile(
                    card = RecapCard.Cover,
                    title = "October 2026",
                    subtitle = "solved",
                    solved = 96,
                    art = RecapTileArt.Mosaic(List(MOSAIC_CELLS) { Color.Blue }, empty = Color.Gray),
                    opened = false,
                    playKey = "2026-10",
                    description = "Open",
                    onOpen = {},
                    tag = "tile",
                    variant = RecapTileSize.Month,
                    entry = entry,
                )
            }
        }
    }

    @Test
    fun `a tile with its entry motion off shows the final count at once`() {
        show(entry = false)
        rule.onNodeWithText("96").assertIsDisplayed()
    }

    @Test
    fun `a tile with its entry motion on counts up from zero`() {
        show(entry = true)
        assertTrue(rule.onAllNodesWithText("96").fetchSemanticsNodes().isEmpty())
    }
}
