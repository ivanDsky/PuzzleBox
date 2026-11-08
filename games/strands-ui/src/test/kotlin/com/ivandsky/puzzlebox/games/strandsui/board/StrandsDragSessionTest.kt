package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.gameui.TipKind
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A5/A12: what one touch on a letter makes of the trace, in cell units over a 6 × 8 board. */
class StrandsDragSessionTest {

    private val rows = 8
    private val cols = 6

    private val start = Coord(3, 1)

    private fun session(down: Offset = centreOf(start), snap: Boolean = false) =
        StrandsDragSession(rows, cols, start, down, snap)

    @Test
    fun `the first letter joined starts the drag at the touched letter`() {
        val steps = session().move(centreOf(Coord(3, 2)), Trace())
        assertEquals(
            listOf(TraceStep.Updated(Trace(listOf(start))), TraceStep.Updated(Trace(listOf(start, Coord(3, 2))))),
            steps,
        )
    }

    @Test
    fun `a drag grows the touched letter, not an earlier tap trace`() {
        val tapped = Trace(listOf(Coord(0, 0)))
        val steps = session().move(centreOf(Coord(3, 2)), tapped)
        assertEquals(TraceStep.Updated(Trace(listOf(start, Coord(3, 2)))), steps.last())
    }

    @Test
    fun `lifting a drag submits its trace`() {
        val session = session()
        val dragged = (session.move(centreOf(Coord(3, 2)), Trace()).last() as TraceStep.Updated).trace
        assertEquals(TraceStep.Submit(listOf(start, Coord(3, 2))), session.end(dragged))
    }

    @Test
    fun `the finger shows only once the touch is a drag`() {
        val session = session()
        session.move(centreOf(start) + Offset(0.1f, 0f), Trace())
        assertNull(session.finger(Trace()))
        val dragged = (session.move(centreOf(Coord(3, 2)), Trace()).last() as TraceStep.Updated).trace
        assertEquals(centreOf(Coord(3, 2)), session.finger(dragged)?.at)
    }

    @Test
    fun `a tail the walk will not grow from gives the tip no stretch`() {
        // An east drag dipping 29° cuts into the diagonal (4, 2) from inside (3, 2), then
        // pulls up-right toward (3, 3): the walk refuses it until the finger comes to (4, 2).
        val session = session()
        var trace = Trace()
        for (point in listOf(Offset(2.05f, 3.8f), Offset(2.6f, 3.3f))) {
            session.move(point, trace).forEach { trace = (it as TraceStep.Updated).trace }
        }
        assertEquals(listOf(start, Coord(4, 2)), trace.path)
        val finger = checkNotNull(session.finger(trace))
        assertEquals(false, finger.armed)
        assertEquals(TipKind.Nudge, strandsTip(trace.path, finger, rows, cols, latch = null)?.kind)

        session.move(centreOf(Coord(4, 2)), trace)
        assertEquals(true, session.finger(trace)?.armed)
    }

    @Test
    fun `a corner touch-down jittering 1px stays a tap`() {
        // A down in the token's top-left corner, already past the diagonal join line; a 1px
        // wobble on a 100px cell is no travel, so no heading is decided and nothing joins.
        val down = Offset(start.col + 0.06f, start.row + 0.06f)
        val session = session(down)
        val jitter = listOf(Offset(0.01f, 0f), Offset(0f, 0f), Offset(0f, 0.01f), Offset(0f, 0f))
        val steps = jitter.flatMap { session.move(down + it, Trace()) }
        assertEquals(emptyList<TraceStep>(), steps)
        assertEquals(TraceStep.Updated(Trace(listOf(start))), session.end(Trace()))
    }

    @Test
    fun `snap mode joins a letter the finger is within the radius of, with no travel needed`() {
        // Down 0.3 from the side letter's centre, a 1px move: the radius holds it, the walk would still be deciding a heading.
        val down = centreOf(Coord(3, 2)) - Offset(0.3f, 0f)
        val steps = session(down, snap = true).move(down + Offset(0.01f, 0f), Trace())
        assertEquals(
            listOf(TraceStep.Updated(Trace(listOf(start))), TraceStep.Updated(Trace(listOf(start, Coord(3, 2))))),
            steps,
        )
    }

    @Test
    fun `snap mode keeps tap semantics until the first join`() {
        val session = session(snap = true)
        assertEquals(emptyList<TraceStep>(), session.move(centreOf(start) + Offset(0.3f, 0.2f), Trace()))
        assertEquals(TraceStep.Updated(Trace(listOf(start))), session.end(Trace()))
    }

    @Test
    fun `snap mode reports no finger even in a drag`() {
        val session = session(snap = true)
        val dragged = (session.move(centreOf(Coord(3, 2)), Trace()).last() as TraceStep.Updated).trace
        assertEquals(listOf(start, Coord(3, 2)), dragged.path)
        assertNull(session.finger(dragged))
        assertEquals(TraceStep.Submit(listOf(start, Coord(3, 2))), session.end(dragged))
    }

    @Test
    fun `snap mode backtracks by radius`() {
        val session = session(snap = true)
        var trace = Trace()
        for (to in listOf(centreOf(Coord(3, 2)), centreOf(Coord(3, 3)), centreOf(Coord(3, 2)) + Offset(0.3f, 0f))) {
            session.move(to, trace).forEach { trace = (it as TraceStep.Updated).trace }
        }
        assertEquals(listOf(start, Coord(3, 2)), trace.path)
    }
}
