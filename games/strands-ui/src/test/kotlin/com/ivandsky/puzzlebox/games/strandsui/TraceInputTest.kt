package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every A12 tap and drag rule, on coordinates alone (the reducer never sees letters). */
class TraceInputTest {

    private val a = Coord(0, 0)
    private val b = Coord(0, 1)
    private val c = Coord(1, 2)
    private val far = Coord(3, 3)

    private fun traceOf(vararg cells: Coord) = Trace(cells.toList())

    @Test
    fun `a tap on an empty trace starts it`() {
        assertEquals(TraceStep.Updated(traceOf(a)), TraceInput.tap(Trace(), a))
    }

    @Test
    fun `a tap on an adjacent unused letter appends it, diagonals included`() {
        assertEquals(TraceStep.Updated(traceOf(a, b)), TraceInput.tap(traceOf(a), b))
        assertEquals(TraceStep.Updated(traceOf(a, b, c)), TraceInput.tap(traceOf(a, b), c))
    }

    @Test
    fun `a tap on the tail submits a word of two or more letters`() {
        assertEquals(TraceStep.Submit(listOf(a, b, c)), TraceInput.tap(traceOf(a, b, c), c))
    }

    @Test
    fun `a tap on a lone tail clears it`() {
        assertEquals(TraceStep.Cleared, TraceInput.tap(traceOf(a), a))
    }

    @Test
    fun `a tap on an earlier letter truncates the trace to it`() {
        assertEquals(TraceStep.Updated(traceOf(a)), TraceInput.tap(traceOf(a, b, c), a))
        assertEquals(TraceStep.Updated(traceOf(a, b)), TraceInput.tap(traceOf(a, b, c), b))
    }

    @Test
    fun `a tap on a letter not adjacent to the tail starts a new trace there`() {
        assertEquals(TraceStep.Updated(traceOf(far)), TraceInput.tap(traceOf(a, b), far))
        // Adjacent to an earlier letter is not enough: only the tail extends.
        assertEquals(TraceStep.Updated(traceOf(Coord(1, 0))), TraceInput.tap(traceOf(a, b, c), Coord(1, 0)))
    }

    @Test
    fun `a drag start always begins a fresh trace`() {
        assertEquals(TraceStep.Updated(traceOf(c)), TraceInput.dragStart(c))
    }

    @Test
    fun `dragging onto an adjacent unused letter appends it`() {
        assertEquals(TraceStep.Updated(traceOf(a, b)), TraceInput.dragTo(traceOf(a), b))
    }

    @Test
    fun `dragging back onto the previous letter backtracks one`() {
        assertEquals(TraceStep.Updated(traceOf(a, b)), TraceInput.dragTo(traceOf(a, b, c), b))
    }

    @Test
    fun `dragging anywhere else leaves the trace unchanged`() {
        val trace = traceOf(a, b, c)
        assertEquals(TraceStep.Updated(trace), TraceInput.dragTo(trace, far))
        assertEquals(TraceStep.Updated(trace), TraceInput.dragTo(trace, a))
        assertEquals(TraceStep.Updated(trace), TraceInput.dragTo(trace, c))
        assertEquals(TraceStep.Updated(Trace()), TraceInput.dragTo(Trace(), a))
    }

    @Test
    fun `a drag ending on two or more letters submits, and on one keeps it as a tap start`() {
        assertEquals(TraceStep.Submit(listOf(a, b)), TraceInput.dragEnd(traceOf(a, b)))
        assertEquals(TraceStep.Updated(traceOf(a)), TraceInput.dragEnd(traceOf(a)))
    }

    @Test
    fun `a tap outside the board clears the trace`() {
        assertEquals(TraceStep.Cleared, TraceInput.outside())
    }
}
