package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord

/**
 * The A12 input rules as a pure reducer, shared by taps (this board) and the drag layer.
 * Adjacency is [Coord.isAdjacentTo], the engine's one definition; a cell joins the path
 * only when it is adjacent to the tail and not already used, so every submitted path is
 * one StrandsPath accepts.
 */
object TraceInput {

    /**
     * Empty trace: start at [at]. The tail again: submit, or clear a lone letter. An earlier
     * letter: truncate to it. An adjacent unused letter: append. Anything else: start over at [at].
     */
    fun tap(trace: Trace, at: Coord): TraceStep {
        val path = trace.path
        if (path.isEmpty()) return startAt(at)
        if (at == path.last()) return if (path.size > 1) TraceStep.Submit(path) else TraceStep.Cleared
        val earlier = path.indexOf(at)
        if (earlier >= 0) return TraceStep.Updated(Trace(path.subList(0, earlier + 1).toList()))
        if (extends(path, at)) return TraceStep.Updated(Trace(path + at))
        return startAt(at)
    }

    fun dragStart(at: Coord): TraceStep = startAt(at)

    /** Over the previous letter: step back one. Over an adjacent unused letter: append. Over any other traced letter: the path is a wall — nothing changes. Else nothing changes. */
    fun dragTo(trace: Trace, at: Coord): TraceStep {
        val path = trace.path
        if (path.size >= 2 && at == path[path.size - 2]) return TraceStep.Updated(Trace(path.dropLast(1)))
        if (path.isNotEmpty() && extends(path, at)) return TraceStep.Updated(Trace(path + at))
        return TraceStep.Updated(trace)
    }

    /** A 1-letter drag stays as the start of a tap trace. */
    fun dragEnd(trace: Trace): TraceStep =
        if (trace.path.size > 1) TraceStep.Submit(trace.path) else TraceStep.Updated(trace)

    fun outside(): TraceStep = TraceStep.Cleared

    private fun startAt(at: Coord): TraceStep = TraceStep.Updated(Trace(listOf(at)))

    private fun extends(path: List<Coord>, at: Coord): Boolean = at !in path && path.last().isAdjacentTo(at)
}
