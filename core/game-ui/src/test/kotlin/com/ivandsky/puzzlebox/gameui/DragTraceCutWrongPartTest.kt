package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sliding the finger back onto the wrong part of the line cuts it there (ticket 1585), over a 5 x 5
 * board of 100 px cells with Zip-like hooks: 4-way adjacency, no revisit. The line is a hook
 * (0,0) (0,1) (0,2) (1,2) (1,1), its tail (1,1) beside the traced (0,1). [cutFrom] says which of
 * its cells count as the wrong part.
 */
class DragTraceCutWrongPartTest {

    private val geometry = GridGeometry(rows = 5, cols = 5, widthPx = 500f, heightPx = 500f)

    private val hook = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2), GridCell(1, 2), GridCell(1, 1))

    private class Hooks(private val cutFrom: Int?) : DragTraceHooks {
        var asked = 0
            private set

        override fun canExtend(path: List<GridCell>, next: GridCell): Boolean {
            if (next in path) return false
            val last = path.lastOrNull() ?: return next == GridCell(0, 0)
            return (next.row == last.row && next.col in last.col - 1..last.col + 1) ||
                (next.col == last.col && next.row in last.row - 1..last.row + 1)
        }

        override fun cutFrom(path: List<GridCell>): Int {
            asked++
            return cutFrom ?: path.size
        }
    }

    private fun centre(row: Int, col: Int) = Offset(col * 100f + 50f, row * 100f + 50f)

    private fun segment(from: Offset, to: Offset, steps: Int = 6) = List(steps) { from + (to - from) * ((it + 1).toFloat() / steps) }

    private val shortOfMiddle = listOf(Offset(150f, 100f), Offset(150f, 70f), Offset(150f, 150f))

    private class Played(val stroke: DragTraceStroke, val hooks: Hooks) {
        val events = mutableListOf<DragEvent>()

        fun feed(points: List<Offset>) = apply { events += stroke.moveTo(points) }
    }

    private fun play(cutFrom: Int?, path: List<GridCell> = hook): Played {
        val hooks = Hooks(cutFrom)
        val stroke = DragTraceStroke(geometry, hooks, path, centre(path.last().row, path.last().col))
        return Played(stroke, hooks).also { it.events += stroke.begin() }
    }

    @Test
    fun `sliding onto an earlier cell of the wrong part cuts the line back to it`() {
        val s = play(cutFrom = 1).feed(segment(centre(1, 1), centre(0, 1)))
        assertEquals(listOf<DragEvent>(DragEvent.Retract(1)), s.events)
        assertEquals(hook.take(2), s.stroke.path)
    }

    @Test
    fun `drawing goes on from the cut in the same stroke`() {
        val s = play(cutFrom = 1).feed(segment(centre(1, 1), centre(0, 1))).feed(segment(centre(0, 1), centre(1, 1)))
        assertEquals(listOf(DragEvent.Retract(1), DragEvent.Extend(GridCell(1, 1))), s.events)
        assertEquals(hook.take(2) + GridCell(1, 1), s.stroke.path)
    }

    @Test
    fun `an earlier cell before the wrong part stays a wall`() {
        val s = play(cutFrom = 2).feed(segment(centre(1, 1), centre(0, 1)))
        assertEquals(listOf<DragEvent>(DragEvent.Refused(GridCell(0, 1))), s.events)
        assertEquals(hook, s.stroke.path)
    }

    @Test
    fun `a finger that stops short of the middle of a traced cell cuts nothing`() {
        // Straight up into the lower part of (0,1), 0.8 of a cell from the tail's middle, and back.
        val s = play(cutFrom = 0).feed(shortOfMiddle)
        assertEquals(emptyList<DragEvent>(), s.events)
        assertEquals(hook, s.stroke.path)
    }

    @Test
    fun `the hooks are asked where the wrong part starts only when the finger is at a traced cell`() {
        // Bug it catches: a solver search per pointer sample on a board with no stored solution.
        val s = play(cutFrom = 1).feed(shortOfMiddle)
        assertEquals(0, s.hooks.asked)
        s.feed(segment(centre(1, 1), centre(0, 1)))
        assertEquals(1, s.hooks.asked)
    }
}
