package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.TipKind
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Zip's live tip (spec A4) over the real [ZipDragHooks]: the finger, projected onto its
 * dominant axis from the tail centre, moves the line end only toward a cell Zip would
 * accept — never through a wall, off the board, over a used cell or an out-of-order
 * waypoint, and never diagonally.
 */
class ZipTraceTipTest {

    // 5x5; waypoint 1 at (0,0), 2 at (0,3), 3 at (1,1); a wall right of (2,0).
    private val board = ZipBoard(
        n = 5,
        walls = setOf(Wall(Cell(10), WallDirection.RIGHT)),
        waypoints = listOf(Cell(0), Cell(3), Cell(6)),
    )
    private val hooks = ZipDragHooks(board)

    private fun cells(vararg rc: Pair<Int, Int>) = rc.map { (r, c) -> GridCell(r, c) }

    /** Finger [dx], [dy] cells away from the centre of the tail. */
    private fun finger(tail: GridCell, dx: Float, dy: Float) =
        Offset(tail.col + 0.5f + dx, tail.row + 0.5f + dy)

    private fun tip(path: List<GridCell>, dx: Float, dy: Float) =
        zipTip(path, finger(path.last(), dx, dy), hooks)

    @Test
    fun `toward a legal cell the tip follows the finger on the axis, capped short of it`() {
        val tip = tip(cells(0 to 0), 0.3f, 0.04f)!!
        assertEquals(TipKind.Forward, tip.kind)
        assertEquals(GridCell(0, 1), tip.toward)
        assertEquals(0.3f, tip.extent, 1e-4f)

        val far = tip(cells(0 to 0), 3f, 0f)!!
        assertEquals(MotionTokens.Trace.TipMaxCells, far.extent, 1e-5f)
    }

    @Test
    fun `a diagonal finger names no axis, so the tip rests`() {
        assertNull(tip(cells(0 to 0), 0.4f, 0.4f))
        assertNull(tip(cells(0 to 0), -0.4f, 0.4f))
        // Just off the diagonal the dominant axis wins; the other component is ignored.
        assertEquals(0.45f, tip(cells(0 to 0), 0.45f, 0.44f)!!.extent, 1e-4f)
    }

    @Test
    fun `the perpendicular part of the finger moves the tip nowhere off the axis`() {
        // Mostly east with a quarter cell of south on top: the lean is the east push alone.
        val tip = tip(cells(0 to 0), 0.3f, 0.25f)!!
        assertEquals(GridCell(0, 1), tip.toward)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `toward a wall there is no tip`() {
        assertNull(tip(cells(0 to 0, 1 to 0, 2 to 0), 0.9f, 0f)) // right of the tail (2,0) is walled
    }

    @Test
    fun `toward the board edge there is no tip`() {
        assertNull(tip(cells(0 to 0), 0f, -2f))
        assertNull(tip(cells(0 to 0), -2f, 0f))
    }

    @Test
    fun `toward a used cell there is no tip, unless it is the previous one`() {
        // Tail (1,0): up is (0,0), already traced and not the previous cell.
        assertNull(tip(cells(0 to 0, 0 to 1, 1 to 1, 1 to 0), 0f, -0.8f))

        val back = tip(cells(0 to 0, 0 to 1), -0.35f, 0f)!!
        assertEquals(TipKind.Back, back.kind)
        assertEquals(GridCell(0, 0), back.toward)
        assertEquals(0.35f, back.extent, 1e-4f)
    }

    @Test
    fun `toward an out-of-order waypoint there is no tip`() {
        // Tail (1,0) right next to waypoint 3 at (1,1) before waypoint 2 at (0,3) is reached.
        assertNull(tip(cells(0 to 0, 1 to 0), 0.8f, 0f))
    }

    @Test
    fun `a finger resting on the tail centre, or an empty path, makes no tip`() {
        assertNull(tip(cells(0 to 0), 0.03f, 0.02f))
        assertNull(zipTip(emptyList(), Offset(0.5f, 0.5f), hooks))
    }
}
