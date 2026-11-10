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
    fun cornerOverlapOfTwoBandsGoesToTheNearerLine() {
        // Corner of the vertical line x = 40 and the horizontal line y = 10.
        val wide = { x: Float, y: Float -> geometry.edgeAt(Offset(x, y), band = 0.4f, inset = 0f) }
        assertEquals(GridEdge(GridCell(1, 1), Side.Right), wide(40.5f, 10.5f)) // 0.025 vs 0.05 cell
        assertEquals(GridEdge(GridCell(0, 1), Side.Right), wide(39f, 9f)) // 0.05 vs 0.1 cell
        assertEquals(GridEdge(GridCell(0, 2), Side.Bottom), wide(43f, 10.5f)) // 0.15 vs 0.05 cell
    }

    @Test
    fun exactlyAtTheCornerTheVerticalLineWins() {
        assertEquals(
            GridEdge(GridCell(1, 1), Side.Right),
            geometry.edgeAt(Offset(40f, 10f), band = 0.4f, inset = 0f),
        )
    }

    @Test
    fun aTapThirtyPercentAlongASideHitsItEvenInsideTheCrossingBand() {
        // The right side of (1, 1) lies on x = 40 from y = 10 to y = 20, and 0.3 of the 10 px cell
        // height from its ends is inside the 0.38 band of the lines y = 10 and y = 20.
        val zip = { x: Float, y: Float -> geometry.edgeAt(Offset(x, y), band = 0.38f, inset = 0f) }
        assertEquals(GridEdge(GridCell(1, 1), Side.Right), zip(40f, 13f))
        assertEquals(GridEdge(GridCell(1, 1), Side.Right), zip(40f, 17f))
        assertEquals(GridEdge(GridCell(0, 1), Side.Bottom), zip(26f, 10f))
        assertEquals(GridEdge(GridCell(0, 1), Side.Bottom), zip(34f, 10f))
    }

    @Test
    fun theDefaultBandAndInsetStillSendACornerTapToTheCell() {
        // Tango's editor uses these values: the corner of two bands stays dead.
        assertNull(geometry.edgeAt(Offset(40.5f, 10.5f)))
        assertNull(geometry.edgeAt(Offset(43f, 10.5f)))
        assertNull(geometry.edgeAt(Offset(40f, 10f)))
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
