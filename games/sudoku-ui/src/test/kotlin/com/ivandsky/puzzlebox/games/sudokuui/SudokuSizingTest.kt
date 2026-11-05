package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

// A15 sizing policy — computed, never eyeballed (QueensSizingTest precedent).
class SudokuSizingTest {

    // What the SinglePane screen takes around the board pane, measured as screen minus the
    // sudoku.board_area bounds in SudokuPlayUiTest: across, the gutters (24dp at 360x640,
    // 24.7dp at 411x891); down, the ~207dp PlayChrome plus gutters (254dp and 256.3dp).
    // The larger of each pair is kept.
    private val widthReserve = 25.dp
    private val heightReserve = 256.dp

    private fun portrait(width: Int, height: Int, size: Int): SudokuLayout =
        SudokuSizing.layout(width.dp - widthReserve, height.dp - heightReserve, size)

    private fun assertFitsStacked(layout: SudokuLayout, width: Int, height: Int, size: Int) {
        val paneWidth = width.dp - widthReserve
        val paneHeight = height.dp - heightReserve
        val used = layout.board + SudokuSizing.stackedKeypadHeight(layout)
        assertTrue("grid + keypad $used overflows $paneHeight", used <= paneHeight)
        assertTrue("board ${layout.board} wider than $paneWidth", layout.board <= paneWidth)
        assertTrue("digit keys ${digitKeyWidth(layout, paneWidth, size)} narrower than 48dp", digitKeyWidth(layout, paneWidth, size) >= 48.dp)
    }

    private fun digitKeyWidth(layout: SudokuLayout, keypadWidth: Dp, size: Int): Dp {
        val perRow = (size + layout.keyRows - 1) / layout.keyRows
        return (keypadWidth - layout.keyGap * (perRow - 1)) / perRow
    }

    @Test
    fun `compact phone 360x640 wraps 9 digits into two rows and gives the board every spare dp`() {
        val nine = portrait(360, 640, 9)
        assertEquals(SudokuLayout(cell = 24.dp, board = 216.dp, key = 48.dp, keyGap = 8.dp, keyRows = 2), nine)
        assertFitsStacked(nine, 360, 640, 9) // 384 pane − 3 × (48 + 8) keypad = 216, exactly the board

        val six = portrait(360, 640, 6)
        assertEquals(SudokuLayout(cell = 45.dp, board = 270.dp, key = 49.dp, keyGap = 8.dp, keyRows = 1), six)
        assertFitsStacked(six, 360, 640, 6)
    }

    @Test
    fun `tall phone 411x891 is width-bound`() {
        val nine = portrait(411, 891, 9)
        assertEquals(SudokuLayout(cell = 42.dp, board = 378.dp, key = 64.dp, keyGap = 8.dp, keyRows = 2), nine)
        assertFitsStacked(nine, 411, 891, 9)

        val six = portrait(411, 891, 6)
        assertEquals(SudokuLayout(cell = 64.dp, board = 384.dp, key = 57.dp, keyGap = 8.dp, keyRows = 1), six)
        assertFitsStacked(six, 411, 891, 6)
    }

    @Test
    fun `tablet portrait 800x1280 caps the board and grows keys to 64dp`() {
        val nine = portrait(800, 1280, 9)
        assertEquals(SudokuLayout(cell = 61.dp, board = 549.dp, key = 64.dp, keyGap = 8.dp, keyRows = 1), nine)
        assertFitsStacked(nine, 800, 1280, 9)

        val six = portrait(800, 1280, 6)
        assertEquals(SudokuLayout(cell = 92.dp, board = PlayBoardMaxWidth, key = 64.dp, keyGap = 8.dp, keyRows = 1), six)
        assertFitsStacked(six, 800, 1280, 6)
    }

    @Test
    fun `tablet landscape stacks a capped board over the controls row and a one-row keypad`() {
        // Tab S9 landscape, 1280x800: TwoPane gives the board pane 62% of the width and the
        // full height, less the 12dp gutters; Undo/Redo/Hint and Notes/Erase/Clear take two
        // action rows under the grid, the digits one more, and all keys stay at 64dp.
        val paneWidth = 1280.dp * 0.62f - 24.dp
        val paneHeight = 800.dp - 24.dp
        val nine = SudokuSizing.layout(paneWidth, paneHeight, 9, actionRows = 2)
        assertEquals(SudokuLayout(cell = 61.dp, board = 549.dp, key = 64.dp, keyGap = 8.dp, keyRows = 1, actionRows = 2), nine)
        assertTrue(nine.board + SudokuSizing.stackedKeypadHeight(nine) <= paneHeight)

        val six = SudokuSizing.layout(paneWidth, paneHeight, 6, actionRows = 2)
        assertEquals(SudokuLayout(cell = 92.dp, board = PlayBoardMaxWidth, key = 64.dp, keyGap = 8.dp, keyRows = 1, actionRows = 2), six)
        assertTrue(six.board + SudokuSizing.stackedKeypadHeight(six) <= paneHeight)
    }

    @Test
    fun `a second action row shrinks the board, never the keys, on a short pane`() {
        // Pixel-class landscape pane, 500x400: the extra row's height comes out of the grid.
        val one = SudokuSizing.layout(500.dp, 400.dp, 9)
        val two = SudokuSizing.layout(500.dp, 400.dp, 9, actionRows = 2)
        assertTrue("$one vs $two", two.board < one.board)
        assertTrue("$two", two.key >= 48.dp)
        assertTrue("$two", two.board + SudokuSizing.stackedKeypadHeight(two) <= 400.dp)
    }

    @Test
    fun `every pane keeps keys at least 48dp and the board a whole multiple of the cell under the cap`() {
        val widths = listOf(200, 320, 360, 411, 600, 800, 1000, 1280)
        val heights = listOf(300, 480, 640, 891, 1280)
        for (size in listOf(6, 9)) for (actionRows in 1..2) for (w in widths) for (h in heights) {
            val layout = SudokuSizing.layout(w.dp, h.dp, size, actionRows)
            val case = "size=$size actionRows=$actionRows ${w}x$h $layout"
            assertTrue(case, layout.key >= 48.dp)
            assertTrue(case, layout.keyRows in 1..2)
            assertTrue(case, layout.board <= PlayBoardMaxWidth)
            assertTrue(case, layout.board <= w.dp)
            assertTrue(case, layout.board + SudokuSizing.stackedKeypadHeight(layout) <= h.dp)
            // Two rows of 5 keys at 48dp plus gaps need 272dp; narrower panes cannot hold the floor.
            if (w >= 272) assertTrue(case, digitKeyWidth(layout, w.dp, size) >= 48.dp)
            assertEquals(case, layout.cell * size, layout.board)
            assertEquals(case, floor(layout.cell.value), layout.cell.value, 0f)
        }
    }
}
