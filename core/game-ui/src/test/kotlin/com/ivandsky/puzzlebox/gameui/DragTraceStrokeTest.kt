package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The trace stroke as pure gesture math, over a 5 x 5 board of 100 px cells (cell (r, c)
 * spans x 100c..100c+100, y 100r..100r+100) with Zip-like hooks: 4-way adjacency, no
 * revisit, the first cell is (0, 0). Fingers are lists of points; each [feed] call is one
 * pointer event, its points the event's history, oldest first.
 */
class DragTraceStrokeTest {

    private val geometry = GridGeometry(rows = 5, cols = 5, widthPx = 500f, heightPx = 500f)

    private class ZipLikeHooks(private val blocked: Set<GridCell> = emptySet()) : DragTraceHooks {
        override fun canExtend(path: List<GridCell>, next: GridCell): Boolean {
            if (next in path || next in blocked) return false
            val last = path.lastOrNull() ?: return next == GridCell(0, 0)
            return (next.row == last.row && next.col in last.col - 1..last.col + 1) ||
                (next.col == last.col && next.row in last.row - 1..last.row + 1)
        }
    }

    private fun centre(row: Int, col: Int) = Offset(col * 100f + 50f, row * 100f + 50f)

    /** A stroke that began where the finger [down] landed, its first events already taken. */
    private fun stroke(path: List<GridCell>, down: Offset, blocked: Set<GridCell> = emptySet()): Pair<DragTraceStroke, MutableList<DragEvent>> {
        val stroke = DragTraceStroke(geometry, ZipLikeHooks(blocked), path, down)
        val events = mutableListOf<DragEvent>()
        events += stroke.begin()
        return stroke to events
    }

    private fun Pair<DragTraceStroke, MutableList<DragEvent>>.feed(points: List<Offset>) {
        second += first.moveTo(points)
    }

    private fun Pair<DragTraceStroke, MutableList<DragEvent>>.feed(point: Offset) = feed(listOf(point))

    @Test
    fun `a refused cell grazed inside one pointer event does not swallow the cells after it`() {
        // Tail (1, 0): the finger dips into the walled (1, 1), then goes on down into (2, 0).
        val s = stroke(listOf(GridCell(0, 0), GridCell(1, 0)), centre(1, 0), blocked = setOf(GridCell(1, 1)))
        s.feed(listOf(Offset(95f, 150f), Offset(110f, 170f), Offset(60f, 230f)))
        assertEquals(listOf<DragEvent>(DragEvent.Refused(GridCell(1, 1)), DragEvent.Extend(GridCell(2, 0))), s.second)
    }

    @Test
    fun `a finger that grazes a side cell's corner on its way along a row never extends that cell`() {
        // East along row 2 from (2, 2), the finger dips over the row boundary into (3, 2)'s corner, then lands in (2, 3).
        val s = stroke(listOf(GridCell(0, 0), GridCell(1, 0), GridCell(2, 0), GridCell(2, 1), GridCell(2, 2)), centre(2, 2))
        s.feed(Offset(295f, 305f))
        s.feed(Offset(330f, 270f))
        assertEquals(listOf<DragEvent>(DragEvent.Extend(GridCell(2, 3))), s.second)
    }

    @Test
    fun `a diagonal drag extends the diagonal cell through a side cell`() {
        val s = stroke(listOf(GridCell(0, 0), GridCell(1, 0), GridCell(2, 0), GridCell(2, 1), GridCell(2, 2)), centre(2, 2))
        s.feed(segment(centre(2, 2), centre(3, 3), steps = 12))
        assertEquals(GridCell(3, 3), s.first.path.last())
        assertEquals(7, s.first.path.size)
    }

    @Test
    fun `a corner cut at a turn still finds the turn cell`() {
        // (2,1) -> (2,2) -> (3,2): the finger leaves (2,1) for (3,2) straight along the diagonal.
        val s = stroke(listOf(GridCell(0, 0), GridCell(1, 0), GridCell(2, 0), GridCell(2, 1)), centre(2, 1))
        s.feed(segment(centre(2, 1), centre(3, 2), steps = 12))
        assertEquals(listOf(GridCell(0, 0), GridCell(1, 0), GridCell(2, 0), GridCell(2, 1), GridCell(2, 2), GridCell(3, 2)), s.first.path)
    }

    @Test
    fun `jitter across the boundary of a just-retracted cell does not extend it again`() {
        val s = stroke(listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2)), centre(0, 2))
        for (x in listOf(195f, 205f, 195f, 205f, 195f)) s.feed(Offset(x, 50f))
        assertEquals(listOf<DragEvent>(DragEvent.Retract(1)), s.second)
    }

    @Test
    fun `a stroke that starts on an earlier traced cell cuts the trace back to it, then carries on from there`() {
        val s = stroke(listOf(GridCell(2, 1), GridCell(2, 2), GridCell(1, 2), GridCell(0, 2)), centre(2, 2))
        s.feed(segment(centre(2, 2), centre(3, 2), steps = 6))
        assertEquals(listOf<DragEvent>(DragEvent.Retract(1), DragEvent.Extend(GridCell(3, 2))), s.second)
    }

    @Test
    fun `drawing onto an earlier traced cell, not just the previous one, cuts the trace back to it`() {
        val s = stroke(listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2), GridCell(1, 2)), centre(1, 2))
        s.feed(segment(centre(1, 2), centre(1, 1), steps = 6)) // extends (1, 1)
        s.feed(segment(centre(1, 1), centre(0, 1), steps = 6)) // adjacent to the tail, but traced earlier
        assertEquals(listOf<DragEvent>(DragEvent.Extend(GridCell(1, 1)), DragEvent.Retract(1)), s.second)
    }

    @Test
    fun `a fast swipe over several cells in one sample extends every one in order`() {
        val s = stroke(listOf(GridCell(0, 0)), centre(0, 0))
        s.feed(centre(0, 4))
        assertEquals((1..4).map { DragEvent.Extend(GridCell(0, it)) }, s.second)
    }

    private fun segment(from: Offset, to: Offset, steps: Int): List<Offset> =
        List(steps) { from + (to - from) * ((it + 1).toFloat() / steps) }
}
