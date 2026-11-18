package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Flat surfaces (nav-rethink M10-8): the selected cell is its face alone, no band under it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuSelectionTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private val mini = SudokuSession(
        ref = null,
        puzzle = SudokuPuzzle(
            SudokuGames.MINI.variant,
            grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
            grid("463251 521436 142563 356142 234615 615324"),
        ),
    )

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SudokuGrid(model = sudokuBoardModel(mini, 35), onSelect = {}, modifier = Modifier.size(360.dp))
            }
        }
    }

    /** The selected cell's pixel `fromBottom` px above its bottom edge, in its column's middle. */
    private fun selectedPixel(fromBottom: Int) = composeRule.onNodeWithTag("sudoku.cell.35").captureToImage().toPixelMap()
        .let { it[it.width / 2, it.height - 1 - fromBottom] }

    @Test
    fun `the strip under the tile is the tile's own face`() {
        show()
        val strip = with(composeRule.density) { 3.dp.toPx() }.toInt()
        assertEquals(LightBoardTokens.sudokuSelection, selectedPixel(strip))
    }
}
