package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightGlyphs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the overlay mount (00 §3): the Spotlight sits inside the grid's frame padding, so a lit cell's rect, cut
 * uniformly out of the overlay's bounds as the overlay draws it, is the rect the grid gives that cell.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuSpotlightGeometryTest {

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

    private fun lighting(cell: Int) =
        Spotlight(SIZE, SIZE, mapOf(cell to SpotlightRole.TARGET), callout = null, SudokuSpotlightGlyphs(mapOf(cell to "4")), playId = 1)

    private fun show(spotlight: Spotlight) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SudokuGrid(sudokuBoardModel(mini, null), onSelect = {}, Modifier.size(360.dp), spotlight = spotlight)
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertRect(expected: Rect, actual: Rect) {
        val message = "expected $expected, was $actual"
        assertEquals(message, expected.left, actual.left, PX_TOLERANCE)
        assertEquals(message, expected.top, actual.top, PX_TOLERANCE)
        assertEquals(message, expected.right, actual.right, PX_TOLERANCE)
        assertEquals(message, expected.bottom, actual.bottom, PX_TOLERANCE)
    }

    @Test
    fun `a lit cell's rect on the 6x6 grid is the grid's own rect for that cell`() {
        show(lighting(LIT))

        val overlay = bounds(SPOTLIGHT_TAG)
        val w = overlay.width / SIZE
        val h = overlay.height / SIZE
        val col = LIT % SIZE
        val row = LIT / SIZE
        val lit = Rect(overlay.left + col * w, overlay.top + row * h, overlay.left + (col + 1) * w, overlay.top + (row + 1) * h)

        assertRect(bounds("sudoku.cell.$LIT"), lit)
    }

    @Test
    fun `the overlay covers the cells exactly, inside the frame padding`() {
        show(lighting(LIT))

        val cells = Rect(bounds("sudoku.cell.0").topLeft, bounds("sudoku.cell.${SIZE * SIZE - 1}").bottomRight)
        assertRect(cells, bounds(SPOTLIGHT_TAG))

        val frame = with(composeRule.density) { gridStrokes(SIZE).box.toPx() }
        assertRect(bounds("sudoku.grid").deflate(frame), bounds(SPOTLIGHT_TAG))
    }

    private companion object {
        const val SIZE = 6
        /** Bottom right: the farthest cell from the frame's origin, where an offset or a scale error is largest. */
        const val LIT = 35
        const val SPOTLIGHT_TAG = "sudoku.spotlight"
        /** The grid's weighted rows and columns round each cell to whole pixels. */
        const val PX_TOLERANCE = 1f
    }
}
