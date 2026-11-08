package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.Coord
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Snap join: a letter joins when the finger is within its radius, on a 6 × 8 board, in cell units. */
class StrandsRadiusJoinTest {

    private val rows = 8
    private val cols = 6
    private val tail = Coord(3, 1)

    /** Joins made walking a finger from the centre of the path's tail to [to], growing the path as the session does. */
    private fun joins(
        to: Offset,
        path: List<Coord> = listOf(tail),
        blocked: Set<Coord> = emptySet(),
        radius: Float = MotionTokens.Trace.SnapHitRadiusCells,
        from: Offset = centreOf(path.last()),
    ): List<Coord> {
        val join = StrandsRadiusJoin(rows, cols, from, blocked, radius)
        var live = path
        val made = mutableListOf<Coord>()
        join.moveTo(to, { live }) { over ->
            made += over
            live = if (live.size > 1 && over == live[live.size - 2]) live.dropLast(1) else live + over
        }
        return made
    }

    @Test
    fun `a side letter joins at 0_39 of its centre and not at 0_41`() {
        val side = Coord(3, 2)
        assertEquals(listOf(side), joins(centreOf(side) - Offset(0.39f, 0f)))
        assertEquals(emptyList<Coord>(), joins(centreOf(side) - Offset(0.41f, 0f)))
    }

    @Test
    fun `the radius is the Snap token`() {
        assertEquals(0.40f, MotionTokens.Trace.SnapHitRadiusCells, 0f)
    }

    @Test
    fun `a diagonal letter joins inside the radius and a side letter never stands in on the way`() {
        val diagonal = Coord(4, 2)
        assertEquals(listOf(diagonal), joins(centreOf(diagonal) - Offset(0.28f, 0.28f)))
        assertEquals(emptyList<Coord>(), joins(centreOf(diagonal) - Offset(0.30f, 0.30f)))
    }

    @Test
    fun `the previous letter joins within the radius of its centre, a backtrack`() {
        val previous = Coord(3, 0)
        val path = listOf(previous, tail)
        assertEquals(listOf(previous), joins(centreOf(previous) + Offset(0.39f, 0f), path))
        assertEquals(emptyList<Coord>(), joins(centreOf(previous) + Offset(0.41f, 0f), path))
    }

    @Test
    fun `a finger resting beside the tail joins nothing`() {
        assertEquals(emptyList<Coord>(), joins(centreOf(tail) + Offset(0.45f, 0f)))
    }

    @Test
    fun `the nearest letter wins when wider discs overlap`() {
        // Between (3,2) and (4,2), nearer the first; at 0.6 both discs hold the point.
        val point = centreOf(Coord(3, 2)) + Offset(0f, 0.4f)
        assertEquals(listOf(Coord(3, 2)), joins(point, radius = 0.6f, from = point))
    }

    @Test
    fun `a letter already in the trace, a blocked letter and an off-board cell never join`() {
        val inPath = Coord(3, 2)
        assertEquals(emptyList<Coord>(), joins(centreOf(inPath), path = listOf(inPath, Coord(2, 1), tail)))
        val found = Coord(3, 2)
        assertEquals(emptyList<Coord>(), joins(centreOf(found), blocked = setOf(found)))
        // At the corner, the clamped point is 0.71 from the centre of the cell off the board: a 0.8 disc would hold it.
        val corner = Coord(0, 0)
        assertEquals(emptyList<Coord>(), joins(Offset(-3f, -3f), path = listOf(corner), radius = 0.8f, from = centreOf(corner)))
    }

    @Test
    fun `a pointer past the edge behaves as the clamped point`() {
        // A letter in the last column is 0.499 from the clamped edge, inside a 0.6 disc, however far past it the pointer is.
        val last = Coord(3, cols - 1)
        val near = Coord(3, cols - 2)
        val far = Offset(cols + 3f, 3.5f)
        assertEquals(listOf(last), joins(far, path = listOf(near), radius = 0.6f, from = far))
        assertEquals(listOf(last), joins(Offset(cols - 0.001f, 3.5f), path = listOf(near), radius = 0.6f, from = far))
    }

    @Test
    fun `a fast swipe through several letters joins each in order`() {
        val path = listOf(Coord(3, 0))
        assertEquals(
            listOf(Coord(3, 1), Coord(3, 2), Coord(3, 3)),
            joins(centreOf(Coord(3, 3)), path),
        )
    }
}
