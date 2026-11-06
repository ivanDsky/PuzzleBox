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
    fun `tablet landscape board pane reaches the cap with one digit row ending in the notes key`() {
        // Tab S9 landscape, 1280x800, TwoPane: the board pane is 62% of the width less the
        // 12dp gutter each side, and 800 less the gutters down. No button row sits above the
        // digits, so the grid takes the cap; keys stay within 1.1x their ~51dp width.
        val paneWidth = 1280.dp * 0.62f - 24.dp
        val paneHeight = 800.dp - 24.dp
        val nine = SudokuSizing.layout(paneWidth, paneHeight, 9, inlineNotes = true)
        assertEquals(SudokuLayout(cell = 61.dp, board = 549.dp, key = 56.dp, keyGap = 8.dp, keyRows = 1, inlineNotes = true), nine)
        assertTrue(nine.board + SudokuSizing.stackedKeypadHeight(nine) <= paneHeight)

        val six = SudokuSizing.layout(paneWidth, paneHeight, 6, inlineNotes = true)
        assertEquals(SudokuLayout(cell = 92.dp, board = PlayBoardMaxWidth, key = 64.dp, keyGap = 8.dp, keyRows = 1, inlineNotes = true), six)
        assertTrue(six.board + SudokuSizing.stackedKeypadHeight(six) <= paneHeight)
    }

    @Test
    fun `the inline notes key gives the board the button row's height on a short pane`() {
        // Pixel-class landscape pane, 600x400: the board is height-bound, so dropping the
        // Notes/Erase/Clear row grows the grid, never the keys past their width share.
        val stacked = SudokuSizing.layout(600.dp, 400.dp, 9)
        val inline = SudokuSizing.layout(600.dp, 400.dp, 9, inlineNotes = true)
        assertTrue("$stacked vs $inline", inline.board > stacked.board)
        assertTrue("$inline", inline.key >= 48.dp)
        assertTrue("$inline", inline.board + SudokuSizing.stackedKeypadHeight(inline) <= 400.dp)
    }

    @Test
    fun `every pane keeps keys at least 48dp and the board a whole multiple of the cell under the cap`() {
        val widths = listOf(200, 320, 360, 411, 600, 800, 1000, 1280)
        val heights = listOf(300, 480, 640, 891, 1280)
        for (size in listOf(6, 9)) for (inline in listOf(false, true)) for (w in widths) for (h in heights) {
            val layout = SudokuSizing.layout(w.dp, h.dp, size, inline)
            val case = "size=$size inline=$inline ${w}x$h $layout"
            assertTrue(case, layout.key >= 48.dp)
            assertTrue(case, layout.keyRows in 1..2)
            assertTrue(case, layout.board <= PlayBoardMaxWidth)
            assertTrue(case, layout.board <= w.dp)
            assertTrue(case, layout.board + SudokuSizing.stackedKeypadHeight(layout) <= h.dp)
            // Two rows of 5 keys at 48dp plus gaps need 272dp; narrower panes cannot hold the floor.
            if (w >= 272) assertTrue(case, digitKeyWidth(layout, w.dp, size) >= 48.dp)
            // The inline row (digits and toggle) lays out in the capped keypad column.
            if (inline && w >= 272) {
                val perRow = (size + 1 + layout.keyRows - 1) / layout.keyRows
                assertTrue(case, SudokuSizing.inlineKeyWidth(minOf(w.dp, PlayBoardMaxWidth), perRow) >= 48.dp)
            }
            assertEquals(case, layout.cell * size, layout.board)
            assertEquals(case, floor(layout.cell.value), layout.cell.value, 0f)
        }
    }
}
