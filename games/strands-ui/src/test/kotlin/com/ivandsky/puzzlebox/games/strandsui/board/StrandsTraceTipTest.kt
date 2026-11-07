package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.Coord
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A4: the live tip follows toward a free letter the walk can join, backs toward the previous one, and only nudges anywhere else. */
class StrandsTraceTipTest {

    private val rows = 4
    private val cols = 4

    private fun finger(tail: Coord, dx: Float, dy: Float, armed: Boolean = true) =
        TraceFinger(centreOf(tail) + Offset(dx, dy), Offset(dx, dy), armed)

    private fun heading(degrees: Float): Offset {
        val rad = Math.toRadians(degrees.toDouble())
        return Offset(cos(rad).toFloat(), sin(rad).toFloat())
    }

    @Test
    fun `toward a free letter the tip follows the finger, then eases short of the next centre`() {
        val tail = Coord(1, 1)
        val near = strandsTip(listOf(tail), finger(tail, 0.3f, 0f), rows, cols, latch = null)!!
        assertEquals(TipKind.Forward, near.kind)
        assertEquals(TraceDirection.East, near.direction)
        assertEquals(0.3f, near.extent, 1e-4f)

        val far = strandsTip(listOf(tail), finger(tail, 3f, 0f), rows, cols, latch = null)!!
        assertTrue(far.extent > MotionTokens.Trace.TipFollowCells && far.extent <= MotionTokens.Trace.TipReachCells)
    }

    @Test
    fun `a diagonal tip measures its reach along the diagonal step`() {
        val tail = Coord(1, 1)
        val tip = strandsTip(listOf(tail), finger(tail, 0.2f, 0.2f), rows, cols, latch = null)!!
        assertEquals(TraceDirection.SouthEast, tip.direction)
        assertEquals(0.2f, tip.extent, 1e-4f)
    }

    @Test
    fun `toward the previous letter the tip backs up`() {
        val path = listOf(Coord(1, 2), Coord(1, 1))
        val tip = strandsTip(path, finger(Coord(1, 1), 0.3f, 0f), rows, cols, latch = null)!!
        assertEquals(TipKind.Back, tip.kind)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `into a used letter or off the board the tip only nudges`() {
        val used = listOf(Coord(1, 2), Coord(2, 2), Coord(2, 1), Coord(1, 1))
        val intoUsed = strandsTip(used, finger(Coord(1, 1), 3f, 0f), rows, cols, latch = null)!!
        assertEquals(TipKind.Nudge, intoUsed.kind)
        assertTrue(intoUsed.extent <= MotionTokens.Trace.TipNudgeCells)

        val corner = Coord(0, 0)
        val offBoard = strandsTip(listOf(corner), finger(corner, 0f, -3f), rows, cols, latch = null)!!
        assertEquals(TipKind.Nudge, offBoard.kind)
        assertEquals(TraceDirection.North, offBoard.direction)
        assertTrue(offBoard.extent <= MotionTokens.Trace.TipNudgeCells)
    }

    @Test
    fun `from a tail the finger has not reached the tip only nudges`() {
        // A wrong diagonal: (2, 1) joined while the finger was still beside (1, 0), never came to it.
        val path = listOf(Coord(1, 0), Coord(2, 1))
        val tip = strandsTip(path, finger(Coord(2, 1), 0.7f, -0.2f, armed = false), rows, cols, latch = null)!!
        assertEquals(TraceDirection.East, tip.direction)
        assertEquals(TipKind.Nudge, tip.kind)
        assertTrue(tip.extent <= MotionTokens.Trace.TipNudgeCells)
    }

    @Test
    fun `from a tail the finger has not reached the tip still backs up`() {
        val path = listOf(Coord(1, 0), Coord(2, 1))
        val tip = strandsTip(path, finger(Coord(2, 1), -0.4f, -0.4f, armed = false), rows, cols, latch = null)!!
        assertEquals(TipKind.Back, tip.kind)
    }

    @Test
    fun `the tip keeps its direction until the travel clears the cone by the latch margin`() {
        val tail = Coord(1, 1)
        val inMargin = heading(MotionTokens.Trace.TipLatchDegrees8Way + StrandsBoardSpec.SideConeDegrees - 1f) * 0.4f
        val beyond = heading(MotionTokens.Trace.TipLatchDegrees8Way + StrandsBoardSpec.SideConeDegrees + 1f) * 0.4f
        val held = strandsTip(listOf(tail), TraceFinger(centreOf(tail) + inMargin, inMargin, armed = true), rows, cols, TraceDirection.East)!!
        assertEquals(TraceDirection.East, held.direction)
        val unlatched = strandsTip(listOf(tail), TraceFinger(centreOf(tail) + inMargin, inMargin, armed = true), rows, cols, latch = null)!!
        assertEquals(TraceDirection.SouthEast, unlatched.direction)
        val moved = strandsTip(listOf(tail), TraceFinger(centreOf(tail) + beyond, beyond, armed = true), rows, cols, TraceDirection.East)!!
        assertEquals(TraceDirection.SouthEast, moved.direction)
    }

    @Test
    fun `a finger resting on the tail centre draws no tip`() {
        val tail = Coord(1, 1)
        assertNull(strandsTip(listOf(tail), finger(tail, MotionTokens.Trace.TipDeadCells / 2, 0f), rows, cols, latch = null))
        assertNull(strandsTip(emptyList(), finger(tail, 0.5f, 0f), rows, cols, latch = null))
    }
}
