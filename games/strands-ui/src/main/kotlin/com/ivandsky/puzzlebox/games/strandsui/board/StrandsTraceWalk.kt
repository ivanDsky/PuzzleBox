package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.games.strands.Coord
import kotlin.math.ceil

/**
 * Which letter a drag joins (A5, prototype 02 variant E), in cell units (x = column, y = row)
 * from the touch-down at [down]. A move is walked in [StrandsBoardSpec.SubStepCells] sub-steps
 * so a fast swipe skips nothing; at each, the direction is the finger's [travel], not the angle
 * from the last centre, so a diagonal drawn beside the centre line stays diagonal.
 *
 * From the tail: nothing joins within [StrandsBoardSpec.StillCells]; the previous letter
 * joins (a backtrack) inside [StrandsBoardSpec.BackJoinCells] of its centre; a diagonal letter
 * once the finger has come [StrandsBoardSpec.DiagonalJoinFraction] of the diagonal step; a
 * side letter only inside [StrandsBoardSpec.SideJoinCells] of its centre. A used, off-board
 * or undecided target joins nothing: no side letter stands in for a diagonal.
 */
internal class StrandsTraceWalk(private val rows: Int, private val cols: Int, down: Offset) {
    private val trail = TravelTrail().apply { push(down) }
    private var at = down

    /** The finger's travel over the last [StrandsBoardSpec.TravelWindowCells]; null until it has gone half that. */
    fun travel(): Offset? = trail.travel(StrandsBoardSpec.TravelWindowCells)

    /** Walks the finger to [to], handing [join] every letter it takes on the way; [path] is the trace so far. */
    fun moveTo(to: Offset, path: () -> List<Coord>, join: (Coord) -> Unit) {
        val from = at
        val steps = ceil((to - from).getDistance() / StrandsBoardSpec.SubStepCells).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val point = from + (to - from) * (i.toFloat() / steps)
            trail.push(point)
            candidate(point, path())?.let(join)
        }
        at = to
    }

    private fun candidate(point: Offset, path: List<Coord>): Coord? {
        val tail = path.lastOrNull() ?: return null
        val back = path.getOrNull(path.size - 2)
        val fromTail = point - centreOf(tail)
        if (fromTail.getDistance() < StrandsBoardSpec.StillCells) return null
        if (back != null && (point - centreOf(back)).getDistance() <= StrandsBoardSpec.BackJoinCells) return back
        val heading = travel() ?: fromTail
        val direction = TraceDirection.of(heading.x, heading.y) ?: return null
        val next = Coord(tail.row + direction.dRow, tail.col + direction.dCol)
        if (next.row !in 0 until rows || next.col !in 0 until cols || next in path) return null
        val joins = if (direction.isDiagonal) {
            val advanced = (fromTail.x * direction.dCol + fromTail.y * direction.dRow) / direction.length
            advanced >= StrandsBoardSpec.DiagonalJoinFraction * direction.length
        } else {
            (point - centreOf(next)).getDistance() <= StrandsBoardSpec.SideJoinCells
        }
        return next.takeIf { joins }
    }
}

internal fun centreOf(cell: Coord): Offset = Offset(cell.col + 0.5f, cell.row + 0.5f)

/**
 * The finger's recent positions with the distance travelled to each, in a fixed ring: the
 * window only ever needs the last quarter cell, and a drag allocates nothing per sample.
 */
private class TravelTrail {
    private val xs = FloatArray(CAPACITY)
    private val ys = FloatArray(CAPACITY)
    private val travelled = FloatArray(CAPACITY)
    private var newest = -1
    private var count = 0

    fun push(point: Offset) {
        var distance = 0f
        if (count > 0) {
            val step = Offset(point.x - xs[newest], point.y - ys[newest]).getDistance()
            if (step < SPACING) return
            distance = travelled[newest] + step
        }
        newest = (newest + 1) % CAPACITY
        xs[newest] = point.x
        ys[newest] = point.y
        travelled[newest] = distance
        if (count < CAPACITY) count++
    }

    /** The vector from where the finger was [window] of travel ago to where it is now. */
    fun travel(window: Float): Offset? {
        if (count == 0) return null
        val end = travelled[newest]
        for (back in 1 until count) {
            val i = (newest - back + CAPACITY) % CAPACITY
            if (end - travelled[i] < window) continue
            // The window starts between this sample and the next newer one.
            val j = (i + 1) % CAPACITY
            val t = (end - window - travelled[i]) / (travelled[j] - travelled[i])
            val x = xs[i] + (xs[j] - xs[i]) * t
            val y = ys[i] + (ys[j] - ys[i]) * t
            return Offset(xs[newest] - x, ys[newest] - y)
        }
        val oldest = (newest - count + 1 + CAPACITY) % CAPACITY
        if (end - travelled[oldest] < window / 2) return null
        return Offset(xs[newest] - xs[oldest], ys[newest] - ys[oldest])
    }

    private companion object {
        // Closer samples than this carry no direction, only jitter.
        const val SPACING = 0.01f

        // A window's worth of samples at SPACING, with room to spare.
        const val CAPACITY = 64
    }
}
