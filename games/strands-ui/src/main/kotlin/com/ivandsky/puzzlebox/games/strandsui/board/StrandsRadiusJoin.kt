package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.Coord
import kotlin.math.ceil

/**
 * Which letter a drag joins under the Snap flag (prototype 02 variant 2/3), in cell units
 * (x = column, y = row): the finger joins a letter when it comes within [radius] of that
 * letter's centre, and nothing else. No travel, heading, cone or arrival rule, so the next
 * letter is where the finger is, not where it is going.
 *
 * The pointer is clamped to the board first, so one past the edge counts as at the edge. A
 * move is walked in [StrandsBoardSpec.SubStepCells] sub-steps so a fast swipe skips nothing.
 * At each point, from the trace's tail: the previous letter joins (a backtrack) inside its
 * disc; otherwise the nearest of the tail's on-board neighbours that is neither in the trace
 * nor in [blocked] and holds the point in its disc. With [radius] under half a cell the
 * discs of neighbours never overlap, so "nearest" only decides for a wider radius.
 */
internal class StrandsRadiusJoin(
    private val rows: Int,
    private val cols: Int,
    down: Offset,
    private val blocked: Set<Coord> = emptySet(),
    private val radius: Float = MotionTokens.Trace.SnapHitRadiusCells,
) {
    /** Where the finger is: the last point walked to, clamped to the board. */
    var at = clamped(down)
        private set

    /** Walks the finger to [to], handing [join] every letter it takes on the way; [path] is the trace so far. */
    fun moveTo(to: Offset, path: () -> List<Coord>, join: (Coord) -> Unit) {
        val target = clamped(to)
        val from = at
        val steps = ceil((target - from).getDistance() / StrandsBoardSpec.SubStepCells).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            candidate(from + (target - from) * (i.toFloat() / steps), path())?.let(join)
        }
        at = target
    }

    private fun candidate(point: Offset, path: List<Coord>): Coord? {
        val tail = path.lastOrNull() ?: return null
        val back = path.getOrNull(path.size - 2)
        if (back != null && (point - centreOf(back)).getDistance() <= radius) return back
        var best: Coord? = null
        var bestDistance = Float.MAX_VALUE
        for (dRow in -1..1) {
            for (dCol in -1..1) {
                if (dRow == 0 && dCol == 0) continue
                val next = Coord(tail.row + dRow, tail.col + dCol)
                if (next.row !in 0 until rows || next.col !in 0 until cols || next in path || next in blocked) continue
                val distance = (point - centreOf(next)).getDistance()
                if (distance <= radius && distance < bestDistance) {
                    best = next
                    bestDistance = distance
                }
            }
        }
        return best
    }

    private fun clamped(point: Offset) = Offset(
        point.x.coerceIn(0f, cols - EDGE_EPSILON),
        point.y.coerceIn(0f, rows - EDGE_EPSILON),
    )

    private companion object {
        // Keeps a clamped point inside the last cell, where an edge-on pointer would sit on the next index.
        const val EDGE_EPSILON = 1e-3f
    }
}
