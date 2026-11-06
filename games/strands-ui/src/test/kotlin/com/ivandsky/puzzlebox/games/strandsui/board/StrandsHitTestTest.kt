package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.gameui.GridGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The board's two hit areas (A12, SC11): a touch-down takes the whole cell, a drag only the
 * inner circle, so a diagonal through a corner never catches the side neighbours.
 */
class StrandsHitTestTest {

    private val pitch = 100f
    private val geometry = GridGeometry(rows = 8, cols = 6, widthPx = 6 * pitch, heightPx = 8 * pitch)

    @Test
    fun `a touch-down anywhere in a cell takes it, off the board is nothing`() {
        assertEquals(Coord(0, 0), StrandsHitTest.cellAt(geometry, 1f, 1f))
        assertEquals(Coord(0, 0), StrandsHitTest.cellAt(geometry, 99f, 99f))
        assertEquals(Coord(7, 5), StrandsHitTest.cellAt(geometry, 599f, 799f))
        assertNull(StrandsHitTest.cellAt(geometry, -1f, 50f))
        assertNull(StrandsHitTest.cellAt(geometry, 50f, -1f))
        assertNull(StrandsHitTest.cellAt(geometry, 600f, 50f))
        assertNull(StrandsHitTest.cellAt(geometry, 50f, 800f))
    }

    @Test
    fun `a drag takes a cell at its centre and inside the circle, never at its corners`() {
        assertEquals(Coord(2, 3), StrandsHitTest.innerCellAt(geometry, 350f, 250f))
        assertEquals(Coord(2, 3), StrandsHitTest.innerCellAt(geometry, 350f + 39f, 250f))
        assertNull(StrandsHitTest.innerCellAt(geometry, 350f + 42f, 250f))
        for ((x, y) in listOf(301f to 201f, 399f to 201f, 301f to 299f, 399f to 299f)) {
            assertNull("corner $x,$y", StrandsHitTest.innerCellAt(geometry, x, y))
        }
        assertNull(StrandsHitTest.innerCellAt(geometry, -10f, -10f))
    }

    @Test
    fun `a diagonal drag from one centre to the next never touches the side neighbours`() {
        val hits = (0..100).mapNotNull { i ->
            val t = i / 100f
            StrandsHitTest.innerCellAt(geometry, 50f + 100f * t, 50f + 100f * t)
        }.distinct()
        assertEquals(listOf(Coord(0, 0), Coord(1, 1)), hits)
    }
}
