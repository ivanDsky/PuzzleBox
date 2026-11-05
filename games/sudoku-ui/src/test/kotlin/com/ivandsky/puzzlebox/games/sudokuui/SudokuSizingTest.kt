package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// A15 sizing policy — computed, never eyeballed (QueensSizingTest precedent).
class SudokuSizingTest {

    // The chrome reserve QueensSizingTest uses: 56dp above the pane and 56dp below it.
    private val chromeReserve = 56.dp + 56.dp

    private fun portrait(width: Int, height: Int, size: Int): SudokuLayout =
        SudokuSizing.layout(width.dp, height.dp - chromeReserve, size, keypadBeside = false)

    private fun assertFitsStacked(layout: SudokuLayout, width: Int, height: Int) {
        val used = layout.board + SudokuSizing.stackedKeypadHeight(layout)
        assertTrue("grid + keypad $used overflows ${height.dp - chromeReserve}", used <= height.dp - chromeReserve)
        assertTrue("board ${layout.board} wider than ${width.dp}", layout.board <= width.dp)
    }

    @Test
    fun `compact phone 360x640 fits grid and keypad with no scroll`() {
        val nine = portrait(360, 640, 9)
        assertEquals(SudokuLayout(cell = 40.dp, board = 360.dp, key = 48.dp, keyGap = 8.dp), nine)
        assertFitsStacked(nine, 360, 640)

        val six = portrait(360, 640, 6)
        assertEquals(SudokuLayout(cell = 60.dp, board = 360.dp, key = 53.dp, keyGap = 8.dp), six)
        assertFitsStacked(six, 360, 640)
    }

    @Test
    fun `tall phone 411x891 is width-bound`() {
        val nine = portrait(411, 891, 9)
        assertEquals(SudokuLayout(cell = 45.dp, board = 405.dp, key = 48.dp, keyGap = 8.dp), nine)
        assertFitsStacked(nine, 411, 891)

        val six = portrait(411, 891, 6)
        assertEquals(SudokuLayout(cell = 68.dp, board = 408.dp, key = 61.dp, keyGap = 8.dp), six)
        assertFitsStacked(six, 411, 891)
    }

    @Test
    fun `tablet portrait 800x1280 caps the board and grows keys to 64dp`() {
        val nine = portrait(800, 1280, 9)
        assertEquals(SudokuLayout(cell = 61.dp, board = 549.dp, key = 64.dp, keyGap = 8.dp), nine)
        assertFitsStacked(nine, 800, 1280)

        val six = portrait(800, 1280, 6)
        assertEquals(SudokuLayout(cell = 92.dp, board = PlayBoardMaxWidth, key = 64.dp, keyGap = 8.dp), six)
        assertFitsStacked(six, 800, 1280)
    }

    @Test
    fun `tablet landscape with the keypad beside caps the board in the board pane`() {
        // Tab S9 landscape, 1280x800: TwoPane gives the board 62% of the width, full height.
        val paneWidth = 1280.dp * 0.62f
        val nine = SudokuSizing.layout(paneWidth, 800.dp, 9, keypadBeside = true)
        assertEquals(SudokuLayout(cell = 61.dp, board = 549.dp, key = 64.dp, keyGap = 8.dp), nine)

        val six = SudokuSizing.layout(paneWidth, 800.dp, 6, keypadBeside = true)
        assertEquals(SudokuLayout(cell = 92.dp, board = PlayBoardMaxWidth, key = 64.dp, keyGap = 8.dp), six)
    }

    @Test
    fun `keypad beside sizes the board against the full height`() {
        val beside = SudokuSizing.layout(800.dp, 480.dp, 9, keypadBeside = true)
        val stacked = SudokuSizing.layout(800.dp, 480.dp, 9, keypadBeside = false)
        assertEquals(477.dp, beside.board) // 480 / 9 → 53dp cells
        assertEquals(333.dp, stacked.board) // (480 − 2×64 keys − 2×8 gaps) / 9 → 37dp cells
    }

    @Test
    fun `every pane keeps keys at least 48dp and the board a whole multiple of the cell under the cap`() {
        val widths = listOf(200, 320, 360, 411, 600, 800, 1000, 1280)
        val heights = listOf(300, 480, 640, 891, 1280)
        for (size in listOf(6, 9)) for (w in widths) for (h in heights) for (beside in listOf(false, true)) {
            val layout = SudokuSizing.layout(w.dp, h.dp, size, beside)
            val case = "size=$size ${w}x$h beside=$beside $layout"
            assertTrue(case, layout.key >= 48.dp)
            assertTrue(case, layout.board <= PlayBoardMaxWidth)
            assertTrue(case, layout.board <= w.dp)
            val keypadBelow = if (beside) 0.dp else SudokuSizing.stackedKeypadHeight(layout)
            assertTrue(case, layout.board + keypadBelow <= h.dp)
            assertEquals(case, layout.cell * size, layout.board)
            assertEquals(case, wholeDp(layout.cell), layout.cell.value, 0f)
        }
    }

    private fun wholeDp(dp: Dp): Float = kotlin.math.floor(dp.value)
}
