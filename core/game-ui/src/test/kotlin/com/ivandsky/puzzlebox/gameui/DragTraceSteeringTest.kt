package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The line follows the finger from the line's end (ticket 1314): Zip #600 on an 8 x 8 board of
 * 100 px cells, 4-way no-revisit hooks. Fingers are in cell units (x = column, y = row).
 */
class DragTraceSteeringTest {

    private val geometry = GridGeometry(rows = 8, cols = 8, widthPx = 800f, heightPx = 800f)

    private class Hooks(private val blocked: Set<GridCell> = emptySet()) : DragTraceHooks {
        override fun canExtend(path: List<GridCell>, next: GridCell): Boolean {
            if (next in path || next in blocked) return false
            val last = path.lastOrNull() ?: return next == GridCell(0, 0)
            return (next.row == last.row && next.col in last.col - 1..last.col + 1) ||
                (next.col == last.col && next.row in last.row - 1..last.row + 1)
        }
    }

    /** Solution of Zip #600 from its start 1 at (5, 4), as the ticket lists it. */
    private val solution: List<GridCell> = run {
        var cell = GridCell(5, 4)
        val cells = mutableListOf(cell)
        for (move in "U2 L1 D2 L1 D1 R4 U4 U1 L4 U1 R5 D7 L7 U1 R1 U1 L1 U5 R1 D4 R1 U2 R3 D3".split(' ')) {
            repeat(move.drop(1).toInt()) {
                cell = when (move[0]) {
                    'U' -> cell.copy(row = cell.row - 1)
                    'D' -> cell.copy(row = cell.row + 1)
                    'L' -> cell.copy(col = cell.col - 1)
                    else -> cell.copy(col = cell.col + 1)
                }
                cells += cell
            }
        }
        cells
    }

    private fun at(x: Float, y: Float) = Offset(x * 100f, y * 100f)

    private fun centre(cell: GridCell) = at(cell.col + 0.5f, cell.row + 0.5f)

    private class Played(val stroke: DragTraceStroke, val events: MutableList<DragEvent>)

    private fun Played.feed(vararg points: Pair<Float, Float>) = apply { events += stroke.moveTo(points.map { at(it.first, it.second) }) }

    private fun play(path: List<GridCell>, down: Offset, blocked: Set<GridCell> = emptySet()): Played {
        val stroke = DragTraceStroke(geometry, Hooks(blocked), path, down)
        return Played(stroke, stroke.begin().toMutableList())
    }

    @Test
    fun `a turn beside the drawn column lands with no step back`() {
        val corner = solution.take(solution.indexOf(GridCell(0, 7)) + 1)
        val s = play(corner, at(7.45f, 0.5f))
        s.feed(7.1f to 0.9f, 7.05f to 1.6f, 6.9f to 0.7f, 7.15f to 2.3f, 7.2f to 3.4f)
        assertEquals(corner + listOf(GridCell(1, 7), GridCell(2, 7), GridCell(3, 7)), s.stroke.path)
        assertEquals(listOf<DragEvent>(DragEvent.Extend(GridCell(1, 7)), DragEvent.Extend(GridCell(2, 7)), DragEvent.Extend(GridCell(3, 7))), s.events)
    }

    @Test
    fun `a wobble that stops short of the previous cell's middle never steps back`() {
        val s = play(solution.take(3), centre(solution[2]))
        s.feed(4.5f to 4.2f, 4.6f to 4.35f, 4.5f to 3.8f)
        assertEquals(emptyList<DragEvent>(), s.events)
    }

    @Test
    fun `a finger that reaches the previous cell's middle steps back exactly one cell`() {
        val s = play(solution.take(3), centre(solution[2]))
        s.feed(4.5f to 4.5f)
        assertEquals(listOf<DragEvent>(DragEvent.Retract(1)), s.events)
        s.feed(4.5f to 4.9f)
        assertEquals("the middle of the previous cell is where the wobble ends", listOf<DragEvent>(DragEvent.Retract(1)), s.events)
    }

    @Test
    fun `a fast swipe off the column's centre line fills every cell to the end`() {
        val s = play(solution.take(61), at(5.5f, 2.5f))
        s.feed(5.95f to 3.0f, 5.95f to 5.6f)
        assertEquals(solution, s.stroke.path)
    }

    @Test
    fun `a taken cell ahead of the finger sends the line into the free cell it leans toward`() {
        val path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2), GridCell(1, 2))
        val s = play(path, centre(GridCell(1, 2)), blocked = setOf(GridCell(2, 2), GridCell(2, 1)))
        s.feed(2.2f to 2.1f)
        assertEquals(path + GridCell(1, 1), s.stroke.path)
    }

    @Test
    fun `with no free cell the finger leans toward the line stays and nothing is undone`() {
        val path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2), GridCell(1, 2))
        val s = play(path, centre(GridCell(1, 2)), blocked = setOf(GridCell(2, 2), GridCell(1, 1)))
        s.feed(2.2f to 2.1f)
        assertEquals(path, s.stroke.path)
        assertEquals(emptyList<DragEvent>(), s.events)
    }

    @Test
    fun `leaning on a blocked cell flashes refused only a full cell past the end`() {
        val path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2), GridCell(1, 2))
        val s = play(path, centre(GridCell(1, 2)), blocked = setOf(GridCell(2, 2), GridCell(1, 1)))
        s.feed(2.5f to 2.3f)
        assertEquals(emptyList<DragEvent>(), s.events)
        s.feed(2.5f to 2.5f, 2.5f to 2.6f)
        assertEquals(listOf<DragEvent>(DragEvent.Refused(GridCell(2, 2))), s.events)
    }

    @Test
    fun `a finger resting or moving under half a cell from the end changes nothing`() {
        val path = solution.take(20)
        val tail = path.last()
        val s = play(path, centre(tail))
        val x = tail.col + 0.5f
        val y = tail.row + 0.5f
        s.feed(x + 0.5f to y, x to y + 0.5f, x - 0.5f to y - 0.5f, x to y)
        assertEquals(path, s.stroke.path)
        assertEquals(emptyList<DragEvent>(), s.events)
    }
}
