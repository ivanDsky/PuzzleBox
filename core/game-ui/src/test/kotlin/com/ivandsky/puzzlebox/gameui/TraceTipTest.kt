package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The live tip's law (spec A4): ONE direction — the finger's dominant grid axis (4-way) or
 * nearest compass (8-way) — an on-axis projection that can not reach the next centre, and
 * nothing toward a cell the game would refuse.
 */
class TraceTipTest {

    private val rows = 5
    private val cols = 5

    private fun neighbour(cell: GridCell, direction: TipDirection) =
        GridCell(cell.row + direction.dRow, cell.col + direction.dCol)

    private fun tip(
        path: List<GridCell>,
        dx: Float,
        dy: Float,
        eightWay: Boolean = false,
        canExtend: (GridCell) -> Boolean = { it.row in 0 until rows && it.col in 0 until cols && it !in path },
    ): TraceTip<GridCell>? {
        if (path.isEmpty()) return null
        val direction = dominantDirection(Offset(dx, dy), eightWay) ?: return null
        return traceTip(path, Offset(dx, dy), direction, ::neighbour, canExtend)
    }

    @Test
    fun `a four-way finger names its dominant axis`() {
        assertEquals(TipDirection.East, dominantDirection(Offset(0.4f, 0.1f), eightWay = false))
        assertEquals(TipDirection.West, dominantDirection(Offset(-0.4f, 0.1f), eightWay = false))
        assertEquals(TipDirection.South, dominantDirection(Offset(0.1f, 0.4f), eightWay = false))
        assertEquals(TipDirection.North, dominantDirection(Offset(-0.1f, -0.4f), eightWay = false))
    }

    @Test
    fun `a four-way diagonal finger names no axis and leans nowhere`() {
        assertNull(dominantDirection(Offset(0.4f, 0.4f), eightWay = false))
        assertNull(dominantDirection(Offset(-0.3f, 0.3f), eightWay = false))
        assertNull(tip(listOf(GridCell(2, 2)), 0.8f, 0.8f))
    }

    @Test
    fun `an eight-way finger names its nearest compass, diagonals included`() {
        assertEquals(TipDirection.East, dominantDirection(Offset(0.4f, 0.05f), eightWay = true))
        assertEquals(TipDirection.SouthEast, dominantDirection(Offset(0.4f, 0.4f), eightWay = true))
        assertEquals(TipDirection.NorthWest, dominantDirection(Offset(-0.4f, -0.4f), eightWay = true))
        assertEquals(TipDirection.SouthWest, dominantDirection(Offset(-0.4f, 0.41f), eightWay = true))
    }

    @Test
    fun `a finger resting on the tail centre leans nowhere`() {
        assertNull(dominantDirection(Offset(MotionTokens.Trace.TipDeadCells / 2, 0f), eightWay = true))
        assertNull(dominantDirection(Offset(0f, 0f), eightWay = false))
    }

    @Test
    fun `toward a cell the game takes the tip follows the finger on the axis`() {
        val tip = tip(listOf(GridCell(2, 2)), 0.3f, 0.05f)!!
        assertEquals(TipKind.Forward, tip.kind)
        assertEquals(GridCell(2, 3), tip.toward)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `the lean is capped short of the next centre however far the finger goes`() {
        val tip = tip(listOf(GridCell(2, 2)), 3f, 0.1f)!!
        assertEquals(MotionTokens.Trace.TipMaxCells, tip.extent, 1e-5f)
        assertTrue(tip.extent < 1f)
    }

    @Test
    fun `a perpendicular finger adds nothing to the lean`() {
        val onAxis = tip(listOf(GridCell(2, 2)), 0.3f, 0f)!!
        val skewed = tip(listOf(GridCell(2, 2)), 0.3f, 0.29f)!!
        assertEquals(onAxis.extent, skewed.extent, 1e-5f)
        assertEquals(0f, TipDirection.East.extentOf(Offset(0f, 5f)), 1e-5f)
    }

    @Test
    fun `a diagonal lean measures its reach along the diagonal step`() {
        val tip = tip(listOf(GridCell(1, 1)), 0.2f, 0.2f, eightWay = true)!!
        assertEquals(TipDirection.SouthEast, dominantDirection(Offset(0.2f, 0.2f), eightWay = true))
        assertEquals(GridCell(2, 2), tip.toward)
        assertEquals(0.2f, tip.extent, 1e-4f)
    }

    @Test
    fun `toward a cell the game refuses there is no tip`() {
        val offBoard = tip(listOf(GridCell(0, 0)), 0f, -0.8f) // north of the top edge
        assertNull(offBoard)
        val refused = tip(listOf(GridCell(2, 2)), 0.8f, 0f, canExtend = { false }) // a wall
        assertNull(refused)
        assertNull(tip(emptyList(), 0.5f, 0f))
    }

    @Test
    fun `toward a used cell the tip rests, unless it is the previous one`() {
        // Tail (2,3); west is (2,2), in the path but not previous.
        val path = listOf(GridCell(2, 2), GridCell(3, 2), GridCell(3, 3), GridCell(2, 3))
        assertNull(tip(path, -0.8f, 0f))

        // Previous cell: the tip backs up along the committed line.
        val back = tip(listOf(GridCell(2, 2), GridCell(2, 3)), -0.35f, 0f)!!
        assertEquals(TipKind.Back, back.kind)
        assertEquals(GridCell(2, 2), back.toward)
        assertEquals(0.35f, back.extent, 1e-4f)
    }
}
