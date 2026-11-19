package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line in open areas (ticket 1572), replayed from the recordings at the moments the ticket
 * lists: Zip #527 (7 x 7) on the tablet and Zip #3 (6 x 6) on the phone, no walls, 100 px cells.
 * Cells and fingers are in the ticket's units: (column, row) from the top-left, each cell's
 * centre at whole numbers. Each sample is one pointer event.
 */
class DragTraceOpenAreaTest {

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

    private fun centre(cell: GridCell) = at(cell.col.toFloat(), cell.row.toFloat())

    /** The cells from [start] by moves such as "R2 U1": right, left, down or up that many cells. */
    private fun walk(start: GridCell, moves: String): List<GridCell> {
        var at = start
        val cells = mutableListOf(at)
        for (move in moves.split(' ')) {
            repeat(move.drop(1).toInt()) {
                at = when (move[0]) {
                    'U' -> at.copy(row = at.row - 1)
                    'D' -> at.copy(row = at.row + 1)
                    'L' -> at.copy(col = at.col - 1)
                    else -> at.copy(col = at.col + 1)
                }
                cells += at
            }
        }
        return cells
    }

    private val zip527 = ZipRules(
        size = 7,
        numbers = listOf(
            cell(4, 0), cell(5, 2), cell(5, 0), cell(3, 6), cell(5, 4), cell(5, 3), cell(3, 4), cell(3, 3),
            cell(3, 2), cell(2, 6), cell(1, 6), cell(1, 4), cell(3, 0), cell(1, 2), cell(1, 3),
        ),
    )

    private val zip3 = ZipRules(
        size = 6,
        numbers = listOf(cell(3, 3), cell(4, 4), cell(1, 4), cell(3, 0), cell(0, 4), cell(5, 4), cell(4, 0), cell(2, 3)),
    )

    /** #527 from 1 to 9, the route the player kept in every attempt. */
    private val to9 = "D2 R1 U2 R1 D6 L3 U1 R2 U2 L1 D1 L1 U2"

    /** #527 at 0:16.20 and 0:18.57: 12 drawn, then up column 0 to (0,1); the corner (0,0) and (1,1) free. */
    private val upColumn0 = walk(zip527.numbers.first(), "$to9 L1 D4 L2 U1 R1 U1 L1 U3")

    private fun play(rules: ZipRules, path: List<GridCell>, down: Offset): Played {
        path.indices.forEach { check(rules.canExtend(path.take(it), path[it])) { "illegal path at ${path[it]}" } }
        val board = GridGeometry(rows = rules.size, cols = rules.size, widthPx = rules.size * 100f, heightPx = rules.size * 100f)
        val stroke = DragTraceStroke(board, rules, path, down)
        return Played(stroke, stroke.begin().toMutableList())
    }

    private fun Played.feed(vararg samples: Pair<Float, Float>) = apply {
        for ((x, y) in samples) events += stroke.moveTo(listOf(at(x, y)))
    }

    @Test
    fun `A - a finger cutting the corner past the line's end turns through the corner and leaves the inside cell free`() {
        val s = play(zip527, upColumn0, at(0.38f, 1.04f)).feed(0.75f to 0.49f, 2.46f to -0.19f)
        assertEquals(upColumn0 + listOf(cell(0, 0), cell(1, 0), cell(2, 0)), s.stroke.path)
        assertFalse(cell(1, 1) in s.stroke.path)
    }

    @Test
    fun `B - a fresh touch diagonal from the line's end picks the line up through the corner`() {
        val s = play(zip527, upColumn0, at(0.74f, 0.41f)).feed(1.55f to 0.11f, 3.04f to -0.02f)
        assertEquals(upColumn0 + listOf(cell(0, 0), cell(1, 0), cell(2, 0), cell(3, 0)), s.stroke.path)
    }

    @Test
    fun `C - a finger deep in an out-of-order number holds the line end as it drifts toward a free side cell`() {
        // 0:15.00: up column 0 past 12 to (0,0), then along row 0 to (2,0); 13 is out of order.
        val path = walk(zip527.numbers.first(), "$to9 L1 D4 L2 U6 R2")
        val s = play(zip527, path, centre(path.last())).feed(2.88f to -0.32f, 3.17f to 0.14f, 3.31f to 0.57f, 3.21f to 0.50f)
        assertEquals(path, s.stroke.path)
        assertTrue("only 13 may flash: ${s.events}", s.events.all { it == DragEvent.Refused(cell(3, 0)) })
    }

    @Test
    fun `held at a blocked number, the line turns once the finger is clearly past the side edge`() {
        val path = walk(zip527.numbers.first(), "$to9 L1 D4 L2 U6 R2")
        val s = play(zip527, path, centre(path.last())).feed(3.17f to 0.14f, 3.2f to 0.5f)
        assertEquals("not yet clearly in row 1", path, s.stroke.path)
        s.feed(3.2f to 0.75f)
        assertEquals(path + listOf(cell(2, 1), cell(3, 1)), s.stroke.path)
    }

    @Test
    fun `a clear sideways finger still takes the step that strands a cell`() {
        // Along row 1 well off the diagonal: the player means (1,1), though it cuts off the corner.
        val s = play(zip527, upColumn0, centre(upColumn0.last())).feed(0.9f to 0.85f)
        assertEquals(upColumn0 + cell(1, 1), s.stroke.path)
    }

    @Test
    fun `corridor - a run from 1 down column 6 takes the same cells as before`() {
        val start = listOf(zip527.numbers.first())
        val s = play(zip527, start, centre(start.last())).feed(
            4.38f to 2.00f, 4.84f to 0.10f, 5.92f to 0.39f, 6.06f to 1.28f,
            6.10f to 2.26f, 6.00f to 3.76f, 5.77f to 4.77f, 5.14f to 5.94f,
        )
        assertEquals(walk(start.last(), "D2 R1 U2 R1 D6 L1"), s.stroke.path)
    }

    @Test
    fun `D - a flick back over the previous cell backs the line up and never pulls it toward an out-of-order number`() {
        // A lift sends the stroke nothing more, so the line stays as these samples leave it.
        val path = walk(zip3.numbers.first(), "D1 R1 D1")
        val s = play(zip3, path, at(4.1f, 5.0f))
            .feed(4.35f to 3.75f, 3.68f to 0.69f, 3.46f to 0.46f, 2.48f to 2.47f, 2.66f to 2.54f)
        assertEquals(DragEvent.Retract(2), s.events.first())
        assertFalse(cell(5, 5) in s.stroke.path)
        assertFalse("6 flashed: ${s.events}", DragEvent.Refused(cell(5, 4)) in s.events)
    }

    @Test
    fun `E - a finger wandering inside the line's end cell commits nothing`() {
        val path = walk(zip3.numbers.first(), "D1 R1")
        val s = play(zip3, path, at(4.3f, 3.6f)).feed(4.25f to 3.7f, 4.1f to 3.9f, 4.0f to 4.0f)
        assertEquals(path, s.stroke.path)
        assertEquals(emptyList<DragEvent>(), s.events)
    }

    @Test
    fun `F - a finger grazing a side cell by a hundredth of a cell does not enter it`() {
        val path = walk(zip3.numbers.first(), "R1 D1 L3 U3 R2 U1 L3 D5 R5 U3")
        val s = play(zip3, path, centre(path.last())).feed(4.94f to 2.57f, 4.49f to 1.93f)
        assertEquals(path, s.stroke.path)
        assertEquals(emptyList<DragEvent>(), s.events)
    }
}
