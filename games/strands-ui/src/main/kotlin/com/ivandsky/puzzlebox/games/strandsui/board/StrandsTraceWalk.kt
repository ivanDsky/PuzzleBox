package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.games.strands.Coord
import kotlin.math.ceil

/**
 * Which letter a drag joins (A5, prototype 02 variant E), in cell units (x = column, y = row)
 * from the touch-down at [down]. A move is walked in [StrandsBoardSpec.SubStepCells] sub-steps
 * so a fast swipe skips nothing; at each, the direction is the finger's [travel], never the
 * angle from the last centre, so a diagonal drawn beside the centre line stays diagonal and a
 * finger resting off-centre, in a token's corner, decides nothing.
 *
 * From the tail: nothing joins within [StrandsBoardSpec.StillCells]; the letter before the
 * tail joins (a backtrack of one) inside [StrandsBoardSpec.BackJoinCells] of its centre —
 * the only way onto the drawn path, which is otherwise a wall: an earlier letter joins
 * nothing, never cuts and never re-joins; any other letter only once the travel has decided
 * a heading: a diagonal letter once the finger has come
 * [StrandsBoardSpec.DiagonalJoinFraction] of the diagonal step, a side letter only inside
 * [StrandsBoardSpec.SideJoinCells] of its centre; a free neighbour whose heart
 * ([StrandsBoardSpec.HeartCells]) the finger has come to joins whatever the heading, so a
 * corner cut that reads diagonal never skips the letter it passed through. A used or off-board target joins nothing:
 * no side letter stands in for a diagonal. Only a tail the finger has reached
 * ([StrandsBoardSpec.ArriveCells]) grows the trace, so a corner cut into the wrong diagonal
 * holds there until the finger backs out or comes to it, or until the finger's heading joins
 * a free letter beside the one before it, which the wrong tail then gives way to.
 */
internal class StrandsTraceWalk(private val rows: Int, private val cols: Int, down: Offset) {
    private val trail = TravelTrail().apply { push(down) }

    /** Where the finger is: the last point walked to. */
    var at = down
        private set

    /** The tail the finger has come within [StrandsBoardSpec.ArriveCells] of since it joined. */
    private var reached: Coord? = null

    /** Set by [candidate] when its letter is a reroute: the tail to give up first, back to the letter before it. */
    private var giveUp: Coord? = null

    /** The finger's travel over the last [StrandsBoardSpec.TravelWindowCells]; null until it has gone half that. */
    fun travel(): Offset? = trail.travel(StrandsBoardSpec.TravelWindowCells)

    /** Walks the finger to [to], handing [join] every letter it takes on the way; [path] is the trace so far. */
    fun moveTo(to: Offset, path: () -> List<Coord>, join: (Coord) -> Unit) {
        val from = at
        val steps = ceil((to - from).getDistance() / StrandsBoardSpec.SubStepCells).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val point = from + (to - from) * (i.toFloat() / steps)
            trail.push(point)
            candidate(point, path())?.let {
                reached = null
                giveUp?.let(join)
                join(it)
            }
        }
        at = to
    }

    private fun candidate(point: Offset, path: List<Coord>): Coord? {
        giveUp = null
        val tail = path.lastOrNull() ?: return null
        val back = path.getOrNull(path.size - 2)
        val fromTail = point - centreOf(tail)
        if (reaches(point, path)) reached = tail
        if (fromTail.getDistance() < StrandsBoardSpec.StillCells) return null
        if (back != null && (point - centreOf(back)).getDistance() <= StrandsBoardSpec.BackJoinCells) return back
        if (reached != tail) return back?.let { reroute(point, path, tail, it) }
        return heartOf(point, path, tail) ?: joinFrom(point, path, tail)
    }

    /** The free letter beside [anchor] the finger is within [StrandsBoardSpec.HeartCells] of the centre of, whatever its heading. */
    private fun heartOf(point: Offset, path: List<Coord>, anchor: Coord): Coord? {
        var best: Coord? = null
        var bestDistance = StrandsBoardSpec.HeartCells
        for (dRow in -1..1) for (dCol in -1..1) {
            val cell = Coord(anchor.row + dRow, anchor.col + dCol)
            if (cell.row !in 0 until rows || cell.col !in 0 until cols || cell in path) continue
            val distance = (point - centreOf(cell)).getDistance()
            if (distance <= bestDistance) {
                best = cell
                bestDistance = distance
            }
        }
        return best
    }

    /** The letter beside [anchor] the finger's heading and position join, if any. */
    private fun joinFrom(point: Offset, path: List<Coord>, anchor: Coord): Coord? {
        val heading = travel() ?: return null
        val direction = TraceDirection.of(heading.x, heading.y)
        val next = Coord(anchor.row + direction.dRow, anchor.col + direction.dCol)
        if (next.row !in 0 until rows || next.col !in 0 until cols || next in path) return null
        val joins = if (direction.isDiagonal) {
            val fromAnchor = point - centreOf(anchor)
            val advanced = (fromAnchor.x * direction.dCol + fromAnchor.y * direction.dRow) / direction.length
            advanced >= StrandsBoardSpec.DiagonalJoinFraction * direction.length
        } else {
            (point - centreOf(next)).getDistance() <= StrandsBoardSpec.SideJoinCells
        }
        return next.takeIf { joins }
    }

    /**
     * The letter beside [back], the letter before the unreached [tail], that the finger's heading
     * and position join as if it were [back]'s tail, or null; the tail then gives way to it ([giveUp]).
     */
    private fun reroute(point: Offset, path: List<Coord>, tail: Coord, back: Coord): Coord? {
        val next = joinFrom(point, path, back) ?: return null
        giveUp = back
        return next
    }

    /** A lone letter is reached by the touch-down itself; a joined one once the finger comes to it. */
    private fun reaches(point: Offset, path: List<Coord>): Boolean {
        val tail = path.last()
        return reached == tail || path.size == 1 || (point - centreOf(tail)).getDistance() <= StrandsBoardSpec.ArriveCells
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
