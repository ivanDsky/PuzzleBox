package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridGeometryTest {

    private val geometry = GridGeometry(rows = 6, cols = 8, widthPx = 800f, heightPx = 600f)

    @Test
    fun `non-square boards divide rows and cols independently (F3-2, Strands 6x8 must fit)`() {
        assertEquals(100f, geometry.cellWidth, 0.001f)
        assertEquals(100f, geometry.cellHeight, 0.001f)
        assertEquals(6, geometry.rows)
        assertEquals(8, geometry.cols)
    }

    @Test
    fun `point maps to the cell containing it`() {
        assertEquals(GridCell(0, 0), geometry.cellAt(0f, 0f))
        assertEquals(GridCell(2, 3), geometry.cellAt(350f, 250f))
        assertEquals(GridCell(5, 7), geometry.cellAt(799.9f, 599.9f))
    }

    @Test
    fun `out-of-board points clamp to the boundary cell`() {
        assertEquals(GridCell(0, 0), geometry.cellAt(-50f, -50f))
        assertEquals(GridCell(5, 7), geometry.cellAt(1200f, 900f))
    }

    @Test
    fun `cell rect and center agree with hit testing`() {
        val cell = GridCell(2, 3)
        val rect = geometry.cellRect(cell)
        assertTrue(rect.width == geometry.cellWidth)
        assertTrue(rect.height == geometry.cellHeight)
        assertEquals(cell, geometry.cellAt(geometry.centerOf(cell)))
    }

    @Test
    fun `zero or negative boards fail fast`() {
        val zeroRows = try {
            GridGeometry(rows = 0, cols = 8, widthPx = 800f, heightPx = 600f)
            null
        } catch (expected: IllegalArgumentException) {
            expected
        }
        val negativeCols = try {
            GridGeometry(rows = 6, cols = -1, widthPx = 800f, heightPx = 600f)
            null
        } catch (expected: IllegalArgumentException) {
            expected
        }
        assertTrue("rows=0 must throw IAE", zeroRows is IllegalArgumentException)
        assertTrue("cols=-1 must throw IAE", negativeCols is IllegalArgumentException)
    }

    @Test
    fun `validity check rejects out-of-range cells`() {
        assertTrue(geometry.isValid(GridCell(0, 0)))
        assertTrue(geometry.isValid(GridCell(5, 7)))
        assertFalse(geometry.isValid(GridCell(6, 0)))
        assertFalse(geometry.isValid(GridCell(0, 8)))
        assertFalse(geometry.isValid(GridCell(-1, 0)))
    }
}
