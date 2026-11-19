package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuPickerPositionTest {

    private val window = IntSize(1000, 2000)
    private val board = IntRect(left = 100, top = 300, right = 700, bottom = 900)
    private val picker = IntSize(width = 300, height = 100)

    private fun at(idx: Int, anchor: IntRect = board, windowSize: IntSize = window, content: IntSize = picker): IntOffset =
        SudokuPickerPosition(idx, size = 6, gap = 20).calculatePosition(anchor, windowSize, LayoutDirection.Ltr, content)

    @Test
    fun `the picker sits under the board, centred on the cell's column`() {
        // Column 2 of 6 is centred at 100 + 2.5 * 100 = 350; the picker is 300 wide.
        assertEquals(IntOffset(200, 920), at(idx = 2))
        assertEquals(IntOffset(200, 920), at(idx = 2 + 6 * 5))
    }

    @Test
    fun `a first or last column keeps the picker inside the board's own width`() {
        assertEquals("the left edge of the board", IntOffset(100, 920), at(idx = 0))
        assertEquals("the right edge of the board", IntOffset(400, 920), at(idx = 5))
    }

    @Test
    fun `a picker wider than the board stays inside the window instead`() {
        val wide = IntSize(width = 700, height = 100)

        assertEquals(IntOffset(0, 920), at(idx = 0, content = wide))
        assertEquals(IntOffset(300, 920), at(idx = 5, content = wide))
    }

    @Test
    fun `with no room below the picker goes above the board`() {
        val short = IntSize(1000, 960)

        assertEquals(IntOffset(200, 180), at(idx = 2, windowSize = short))
    }

    @Test
    fun `with room neither below nor above it is pressed against the bottom edge`() {
        val tight = IntSize(1000, 960)
        val top = IntRect(left = 100, top = 40, right = 700, bottom = 900)

        assertEquals(IntOffset(200, 860), at(idx = 2, anchor = top, windowSize = tight))
    }
}
