package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.TipKind
import com.ivandsky.puzzlebox.gameui.TraceTip
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live tip (spec A4): the finger, projected onto one grid axis from the tail centre,
 * moves the line end only toward a cell Zip would accept; toward a wall, the edge, a used
 * cell or an out-of-order waypoint it gives at most [MotionTokens.Trace.TipNudgeCells].
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

    /** Finger [dx], [dy] cells away from the centre of [tail]. */
    private fun finger(tail: GridCell, dx: Float, dy: Float) =
        Offset(tail.col + 0.5f + dx, tail.row + 0.5f + dy)

    private fun tip(path: List<GridCell>, dx: Float, dy: Float, latch: TipAxis? = null) =
        traceTip(path, finger(path.last(), dx, dy), rows = 5, cols = 5, hooks = hooks, latch = latch)

    @Test
    fun `toward a legal cell the tip follows the finger one to one`() {
        val tip = tip(cells(0 to 0), dx = 0.3f, dy = 0.04f)!!
        assertEquals(TipKind.Forward, tip.kind)
        assertEquals(TipAxis.Right, tip.direction)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `a far finger toward a legal cell eases below the reach cap`() {
        val tip = tip(cells(0 to 0), dx = 0f, dy = 3f)!!
        assertEquals(TipKind.Forward, tip.kind)
        assertEquals(TipAxis.Down, tip.direction)
        assertTrue(tip.extent > MotionTokens.Trace.TipFollowCells)
        assertTrue(tip.extent < MotionTokens.Trace.TipReachCells)
    }

    @Test
    fun `toward a wall the tip is capped at the nudge`() {
        val tip = tip(cells(0 to 0, 1 to 0, 2 to 0), dx = 0.9f, dy = 0f)!!
        assertNudge(tip, TipAxis.Right)
    }

    @Test
    fun `toward the board edge the tip is capped at the nudge`() {
        val tip = tip(cells(0 to 0), dx = 0f, dy = -2f)!!
        assertNudge(tip, TipAxis.Up)
    }

    @Test
    fun `toward a used cell the tip is capped at the nudge`() {
        // Tail (1,0): up is (0,0), already traced and not the previous cell.
        val tip = tip(cells(0 to 0, 0 to 1, 1 to 1, 1 to 0), dx = 0f, dy = -0.8f)!!
        assertNudge(tip, TipAxis.Up)
    }

    @Test
    fun `toward an out-of-order waypoint the tip is capped at the nudge`() {
        // Tail (1,0) right next to waypoint 3 at (1,1) before waypoint 2 is reached.
        val tip = tip(cells(0 to 0, 1 to 0), dx = 0.8f, dy = 0f)!!
        assertNudge(tip, TipAxis.Right)
    }

    @Test
    fun `pulling back toward the previous cell shortens the line under the finger`() {
        val tip = tip(cells(0 to 0, 0 to 1), dx = -0.35f, dy = 0f)!!
        assertEquals(TipKind.Back, tip.kind)
        assertEquals(TipAxis.Left, tip.direction)
        assertEquals(0.35f, tip.extent, 1e-4f)
    }

    @Test
    fun `a finger resting on the tail centre makes no tip`() {
        assertNull(tip(cells(0 to 0), dx = 0.03f, dy = 0.02f))
    }

    @Test
    fun `the tip keeps its axis until the finger clearly leaves it`() {
        // 50 degrees below the right axis: past the diagonal, inside the latch band.
        val held = tip(cells(0 to 0), dx = 0.32f, dy = 0.38f, latch = TipAxis.Right)!!
        assertEquals(TipAxis.Right, held.direction)
        val switched = tip(cells(0 to 0), dx = 0.1f, dy = 0.4f, latch = TipAxis.Right)!!
        assertEquals(TipAxis.Down, switched.direction)
    }

    private fun assertNudge(tip: TraceTip<TipAxis>, axis: TipAxis) {
        assertEquals(TipKind.Nudge, tip.kind)
        assertEquals(axis, tip.direction)
        assertTrue("nudge ${tip.extent} > 0", tip.extent > 0f)
        assertTrue("nudge ${tip.extent} ≤ cap", tip.extent <= MotionTokens.Trace.TipNudgeCells)
    }
}
