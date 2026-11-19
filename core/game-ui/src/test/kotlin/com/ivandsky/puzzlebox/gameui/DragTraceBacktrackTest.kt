package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drawing the line back round its corners (ticket 1572, tablet check of round 1): Zip #527 on the
 * 7 x 7 tablet board, no walls, 100 px cells. Cells and fingers are in the ticket's units:
 * (column, row) from the top-left, each cell's centre at whole numbers. Each sample is one
 * pointer event. The solution starts 1 (4, 0) down, right, up to 3 (5, 0), right, then down
 * column 6, so its corners at (5, 0) and (6, 0) are where a finger backing up rounds a turn.
 */
class DragTraceBacktrackTest {

    /** Zip's rules on an open [size] x [size] board numbered [numbers], 1 first. */
    private class ZipRules(val size: Int, val numbers: List<GridCell>) : DragTraceHooks {
        override fun canExtend(path: List<GridCell>, next: GridCell): Boolean {
            if (!inside(next) || next in path) return false
            val last = path.lastOrNull() ?: return next == numbers.first()
            if (abs(next.row - last.row) + abs(next.col - last.col) != 1) return false
            val number = numbers.indexOf(next)
            return number < 0 || number == path.count { it in numbers }
        }

        // A free cell beside the tail needs a way in and a way out, the last number only a way in.
        override fun strands(path: List<GridCell>, next: GridCell): Boolean {
            val drawn = path.toSet() + next
            return sides(path.last()).any { cell ->
                cell !in drawn && sides(cell).count { it == next || it !in drawn } < if (cell == numbers.last()) 1 else 2
            }
        }

        private fun inside(cell: GridCell) = cell.row in 0 until size && cell.col in 0 until size

        private fun sides(cell: GridCell) = listOf(
            GridCell(cell.row - 1, cell.col),
            GridCell(cell.row + 1, cell.col),
            GridCell(cell.row, cell.col - 1),
            GridCell(cell.row, cell.col + 1),
        ).filter(::inside)
    }

    private class Played(val stroke: DragTraceStroke, val events: MutableList<DragEvent>)

    private fun cell(col: Int, row: Int) = GridCell(row, col)

    private fun at(x: Float, y: Float) = Offset((x + 0.5f) * 100f, (y + 0.5f) * 100f)

    private val zip527 = ZipRules(
        size = 7,
        numbers = listOf(
            cell(4, 0), cell(5, 2), cell(5, 0), cell(3, 6), cell(5, 4), cell(5, 3), cell(3, 4), cell(3, 3),
            cell(3, 2), cell(2, 6), cell(1, 6), cell(1, 4), cell(3, 0), cell(1, 2), cell(1, 3),
        ),
    )

    /** The start of #527's solution: 1 (4, 0) D2 R1 U2 R1 D2. */
    private val solution = listOf(
        cell(4, 0), cell(4, 1), cell(4, 2), cell(5, 2), cell(5, 1), cell(5, 0), cell(6, 0), cell(6, 1), cell(6, 2),
    )

    private val geometry = GridGeometry(rows = 7, cols = 7, widthPx = 700f, heightPx = 700f)

    private fun play(path: List<GridCell>, down: Offset): Played {
        val stroke = DragTraceStroke(geometry, zip527, path, down)
        return Played(stroke, stroke.begin().toMutableList())
    }

    private fun Played.feed(vararg points: Pair<Float, Float>) = apply {
        events += stroke.moveTo(points.map { at(it.first, it.second) })
    }

    @Test
    fun `forward four cells then back two round a corner the finger cuts shortens the line by two`() {
        val s = play(solution.take(4), at(5f, 2f))
        s.feed(5f to 1.4f, 5f to 0.8f, 5.1f to 0.3f, 5.4f to 0.05f, 5.8f to 0.05f, 6f to 0.35f, 6f to 0.8f, 6f to 1f)
        assertEquals("drawn forward up column 5, along row 0 and down column 6", solution.take(8), s.stroke.path)

        s.events.clear()
        // Back up column 6 and round the (6, 0) corner wide, never within half a cell of its middle.
        s.feed(6f to 0.7f, 5.6f to 0.3f, 5.3f to 0.05f, 5.05f to 0f)
        assertEquals(listOf<DragEvent>(DragEvent.Retract(6), DragEvent.Retract(5)), s.events)
        assertEquals(solution.take(6), s.stroke.path)
    }

    @Test
    fun `a finger backing round a corner beside the line never sends the line into the cell beside it`() {
        val s = play(solution.take(7), at(6f, 0.3f))
        // Back along row 0 riding 0.3 of a cell below the line, then round (5, 0) down column 5.
        s.feed(5.7f to 0.3f, 5.3f to 0.3f, 5.05f to 0.6f, 5f to 1f, 5f to 1.3f)
        assertEquals(listOf<DragEvent>(DragEvent.Retract(5), DragEvent.Retract(4)), s.events)
        assertEquals(solution.take(5), s.stroke.path)
    }

    @Test
    fun `a finger back in the corner cell but not past it toward the cell before keeps the line`() {
        val s = play(solution.take(7), at(6f, 0f))
        s.feed(5.6f to 0.2f, 5.3f to 0.35f, 5.25f to 0.45f, 5.5f to 0.3f)
        assertEquals(emptyList<DragEvent>(), s.events)
        assertEquals(solution.take(7), s.stroke.path)
    }
}
