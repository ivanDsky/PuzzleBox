package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceInput
import com.ivandsky.puzzlebox.games.strandsui.TraceStep

/**
 * One touch on the board's [start] letter, in cell units from the touch-down at [down]: what
 * the finger's moves make of the trace. The touch waits as a tap until [StrandsTraceWalk]
 * joins another letter; that join turns it into a drag that starts at [start], and every
 * letter joined is a [TraceInput.dragTo]. Until then the walk grows [start] alone, not the
 * trace a tap left, so a drag never extends an earlier tap trace.
 */
internal class StrandsDragSession(rows: Int, cols: Int, private val start: Coord, down: Offset) {
    private val walk = StrandsTraceWalk(rows, cols, down)
    private val alone = listOf(start)
    private var dragging = false

    /** The steps the finger moving to [at] makes of [trace], the live trace before the move, in order; empty while nothing joins. */
    fun move(at: Offset, trace: Trace): List<TraceStep> {
        var live = trace
        var steps = emptyList<TraceStep>()
        fun emit(step: TraceStep) {
            steps = steps + step
            if (step is TraceStep.Updated) live = step.trace
        }
        walk.moveTo(at, { if (dragging) live.path else alone }) { over ->
            if (!dragging) {
                dragging = true
                emit(TraceInput.dragStart(start))
            }
            emit(TraceInput.dragTo(live, over))
        }
        return steps
    }

    /** The lift: a drag ends with [trace]; a touch that joined nothing is a tap on [start]. */
    fun end(trace: Trace): TraceStep = if (dragging) TraceInput.dragEnd(trace) else TraceInput.tap(trace, start)
}
