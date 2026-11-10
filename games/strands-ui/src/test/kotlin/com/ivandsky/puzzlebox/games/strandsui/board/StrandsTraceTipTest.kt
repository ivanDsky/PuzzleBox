package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.TipKind
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Strands' live tip (spec A4): the finger, projected onto the nearest compass from the tail
 * centre, leans the connector's end only toward a letter the board would join next — on the
 * board, free, not found — or back at the previous one; anywhere else it rests.
 */
class StrandsTraceTipTest {

    private val rows = 4
    private val cols = 4

    private val free: (Coord) -> Boolean = { false }

    /** Finger [dx], [dy] cells away from the centre of [tail]. */
    private fun finger(tail: Coord, dx: Float, dy: Float) = centreOf(tail) + Offset(dx, dy)

    private fun tip(path: List<Coord>, tailDx: Float, tailDy: Float, found: (Coord) -> Boolean = free) =
        strandsTip(path, finger(path.last(), tailDx, tailDy), rows, cols, found)

    @Test
    fun `toward a free letter the tip follows the finger, to its square target's near edge and no further`() {
        val tail = Coord(1, 1)
        val near = tip(listOf(tail), 0.3f, 0f)!!
        assertEquals(TipKind.Forward, near.kind)
        assertEquals(Coord(1, 2), near.toward)
        assertEquals(0.3f, near.extent, 1e-4f)

        val edge = 1f - StrandsBoardSpec.TargetHalf
        assertEquals(edge - 0.01f, tip(listOf(tail), edge - 0.01f, 0f)!!.extent, 1e-4f)
        assertNull("past the near edge the letter was refused", tip(listOf(tail), edge + 0.01f, 0f))
        assertNull(tip(listOf(tail), 3f, 0f))
    }

    @Test
    fun `a diagonal tip ends where the finger has come the target's near edge along the diagonal`() {
        val tail = Coord(1, 1)
        val edge = 1f - StrandsBoardSpec.TargetHalf
        assertEquals(edge - 0.01f, tip(listOf(tail), edge - 0.01f, edge - 0.01f)!!.extent, 1e-4f)
        assertNull(tip(listOf(tail), edge + 0.01f, edge + 0.01f))
        // Off the line, 0.9 across and 0.5 down is 0.7 along the step: past the edge, so refused.
        assertNull(tip(listOf(tail), 0.9f, 0.5f))
    }

    @Test
    fun `a finger the walk refused a letter leaves no link to it, though it points at it`() {
        // The finger takes O from G, then goes 0.5 east of O's centre and south to 1.1 pitches
        // below it: 0.5 from the centre of the G below O at the nearest, outside that letter's
        // 0.4 square target, so the walk joins nothing, yet the finger
        // points south of O, so the tip once leaned the connector 0.9 of the way into that G:
        // a link to a letter outside the path.
        val rows = 8
        val cols = 6
        val g = Coord(5, 4)
        val o = Coord(5, 3)
        val session = StrandsDragSession(rows, cols, g, centreOf(g))
        var trace = Trace()
        for (point in listOf(centreOf(o), Offset(4.0f, 5.5f), Offset(4.0f, 6.6f))) {
            session.move(point, trace).forEach { trace = (it as TraceStep.Updated).trace }
        }
        assertEquals(listOf(g, o), trace.path)

        assertNull(strandsTip(trace.path, session.finger()!!, rows, cols, free))
    }

    @Test
    fun `a diagonal tip measures its reach along the diagonal step`() {
        val tail = Coord(1, 1)
        val tip = tip(listOf(tail), 0.2f, 0.2f)!!
        assertEquals(Coord(2, 2), tip.toward)
        assertEquals(0.2f, tip.extent, 1e-4f)
    }

    @Test
    fun `the perpendicular part of the finger moves the tip nowhere off its compass`() {
        // Well inside East's cone (±22.5°), so the south drift changes nothing.
        val tip = tip(listOf(Coord(1, 1)), 0.3f, 0.1f)!!
        assertEquals(Coord(1, 2), tip.toward)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `toward the previous letter the tip backs up`() {
        val path = listOf(Coord(1, 2), Coord(1, 1))
        val tip = tip(path, 0.3f, 0f)!!
        assertEquals(TipKind.Back, tip.kind)
        assertEquals(Coord(1, 2), tip.toward)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `into a used letter or off the board there is no tip`() {
        // Tail (1,1); east is (1,2), in the path but not the previous letter.
        val used = listOf(Coord(1, 2), Coord(2, 2), Coord(2, 1), Coord(1, 1))
        assertNull(tip(used, 0.8f, 0f))

        val corner = Coord(0, 0)
        assertNull(tip(listOf(corner), 0f, -0.8f))
    }

    @Test
    fun `toward a found letter there is no tip`() {
        val tail = Coord(1, 1)
        assertNull(tip(listOf(tail), 0.8f, 0f, found = { it == Coord(1, 2) }))
    }

    @Test
    fun `a finger resting on the tail centre draws no tip`() {
        val tail = Coord(1, 1)
        assertNull(tip(listOf(tail), MotionTokens.Trace.TipDeadCells / 2, 0f))
        assertNull(strandsTip(emptyList(), finger(tail, 0.5f, 0f), rows, cols, free))
    }
}
