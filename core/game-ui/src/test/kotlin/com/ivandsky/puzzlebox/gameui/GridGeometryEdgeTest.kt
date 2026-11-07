package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.gameui.GridEdge.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridGeometryEdgeTest {

    // 3 rows x 4 cols of 20 wide x 10 tall cells: non-square on purpose.
    private val geometry = GridGeometry(rows = 3, cols = 4, widthPx = 80f, heightPx = 30f)

    @Test
    fun pointInsideVerticalBandReturnsRightEdgeOfLeftCell() {
        assertEquals(GridEdge(GridCell(1, 1), Side.Right), geometry.edgeAt(Offset(41f, 15f)))
        assertEquals(GridEdge(GridCell(1, 1), Side.Right), geometry.edgeAt(Offset(38f, 15f)))
    }

    @Test
    fun pointInsideHorizontalBandReturnsBottomEdgeOfUpperCell() {
        assertEquals(GridEdge(GridCell(0, 2), Side.Bottom), geometry.edgeAt(Offset(50f, 10.5f)))
        assertEquals(GridEdge(GridCell(0, 2), Side.Bottom), geometry.edgeAt(Offset(50f, 9.5f)))
    }

    @Test
    fun pointOutsideBandReturnsNull() {
        assertNull(geometry.edgeAt(Offset(50f, 15f)))
        assertNull(geometry.edgeAt(Offset(45f, 15f)))
    }

    @Test
    fun pointInsideInsetAtEitherEndReturnsNull() {
        assertNull(geometry.edgeAt(Offset(40f, 11.5f), band = 0.1f))
        assertNull(geometry.edgeAt(Offset(40f, 18.5f), band = 0.1f))
        assertNull(geometry.edgeAt(Offset(43f, 10f), band = 0.1f))
        assertNull(geometry.edgeAt(Offset(57f, 10f), band = 0.1f))
        assertEquals(
            GridEdge(GridCell(1, 1), Side.Right),
            geometry.edgeAt(Offset(40f, 12.5f), band = 0.1f),
        )
    }

    @Test
    fun zeroInsetCoversTheWholeEdgeExceptTheCorner() {
        assertEquals(
            GridEdge(GridCell(1, 1), Side.Right),
            geometry.edgeAt(Offset(40f, 15f), band = 0.4f, inset = 0f),
        )
    }

    @Test
    fun outerBorderReturnsNull() {
        assertNull(geometry.edgeAt(Offset(0.5f, 15f)))
        assertNull(geometry.edgeAt(Offset(79.5f, 15f)))
        assertNull(geometry.edgeAt(Offset(30f, 0.5f)))
        assertNull(geometry.edgeAt(Offset(30f, 29.5f)))
        assertNull(geometry.edgeAt(Offset(-1f, 15f)))
        assertNull(geometry.edgeAt(Offset(30f, 31f)))
    }

    @Test
    fun cornerOverlapOfTwoBandsReturnsNull() {
        assertNull(geometry.edgeAt(Offset(40.5f, 10.5f), band = 0.4f, inset = 0f))
        assertNull(geometry.edgeAt(Offset(39f, 9.5f), band = 0.4f, inset = 0f))
    }

    @Test
    fun everyInteriorEdgeIsNamedFromItsUpperOrLeftCell() {
        for (row in 0 until geometry.rows) for (col in 0 until geometry.cols - 1) {
            val point = Offset((col + 1) * geometry.cellWidth, (row + 0.5f) * geometry.cellHeight)
            assertEquals(GridEdge(GridCell(row, col), Side.Right), geometry.edgeAt(point))
        }
        for (row in 0 until geometry.rows - 1) for (col in 0 until geometry.cols) {
            val point = Offset((col + 0.5f) * geometry.cellWidth, (row + 1) * geometry.cellHeight)
            assertEquals(GridEdge(GridCell(row, col), Side.Bottom), geometry.edgeAt(point))
        }
    }
}
