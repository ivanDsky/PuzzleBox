package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC11 / A37 sizing on the real 6×8 board: every form factor keeps a 48 dp cell or more, a
 * big pane stops at the 96 dp cap, and a short two-pane binds on height.
 */
class StrandsSizingTest {

    private fun cell(width: Dp, height: Dp) = strandsCellSizeFor(width, height, rows = 8, cols = 6)

    @Test
    fun `phone portrait fits the width, above the floor`() {
        // 360 dp wide, minus the screen's 16 dp margins; 640 tall minus the chrome above.
        val size = cell(328.dp, 460.dp)
        assertEquals(328.dp / 6, size)
        assertTrue(size >= StrandsBoardSpec.TouchFloor)
    }

    @Test
    fun `phone landscape keeps the 48 dp floor and leaves the host to scroll`() {
        assertEquals(48.dp, cell(640.dp, 200.dp))
    }

    @Test
    fun `tablet portrait stops at the cap`() {
        assertEquals(StrandsBoardSpec.CellMaxSize, cell(800.dp, 1100.dp))
    }

    @Test
    fun `tablet landscape two-pane binds on height`() {
        assertEquals(55.dp, cell(635.dp, 440.dp))
    }

    @Test
    fun `an unbounded height sizes from the width`() {
        assertEquals(50.dp, cell(300.dp, Dp.Infinity))
    }

    @Test
    fun `a lowered floor lets a short slot fit the whole board`() {
        assertEquals(25.dp, strandsCellSizeFor(378.dp, 200.dp, rows = 8, cols = 6, floor = 0.dp))
    }
}
