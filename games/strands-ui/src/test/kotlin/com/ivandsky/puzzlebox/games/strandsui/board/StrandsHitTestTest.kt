package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.gameui.GridGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SC11: a touch-down takes the whole cell; off the board is nothing. */
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
}
