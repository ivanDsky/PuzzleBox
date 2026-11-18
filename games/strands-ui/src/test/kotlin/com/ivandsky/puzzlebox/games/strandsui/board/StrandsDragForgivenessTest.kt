package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three Zip-recording failures (#1314) on Strands' own drag, finger paths in cell units
 * (x = column, y = row) over a 6 × 8 board: a turn beside used letters, a wobble back toward
 * the previous letter, and a fast swipe a little off the centre line. Sides and diagonals.
 */
class StrandsDragForgivenessTest {

    private val rows = 8
    private val cols = 6

    @Test
    fun `a turn beside used letters, finger 0_45 off the free line toward them, joins the free letters`() {
        // Row 2 east to (2,4), down to (3,4), then west along row 3 with the finger 0.45 above its centre, toward used row 2.
        val lead = listOf(centre(2, 0), centre(2, 4), centre(3, 4))
        val turn = lead + listOf(Offset(4.1f, 3.05f), Offset(2.5f, 3.05f))
        assertEquals(
            listOf(c(2, 0), c(2, 1), c(2, 2), c(2, 3), c(2, 4), c(3, 4), c(3, 3), c(3, 2)),
            drag(turn, FINE),
        )
    }

    @Test
    fun `a diagonal turn beside used letters joins the next free diagonal letter`() {
        // Row 2 east to (2,3), down-left diagonal to (3,2)... then on to (4,1) with the finger 0.4 toward used (2,2)/(2,3) side.
        val lead = listOf(centre(2, 0), centre(2, 3), centre(3, 2))
        val turn = lead + listOf(Offset(2.5f, 3.5f) + Offset(0.4f, -0.1f), centre(4, 1) + Offset(0.4f, -0.1f))
        assertEquals(
            listOf(c(2, 0), c(2, 1), c(2, 2), c(2, 3), c(3, 2), c(4, 1)),
            drag(turn, FINE),
        )
    }

    @Test
    fun `a wobble back toward the previous letter that stops short of its middle removes nothing`() {
        val before = listOf(c(3, 1), c(3, 2), c(3, 3))
        val side = listOf(centre(3, 1), centre(3, 3), Offset(2.9f, 3.5f), centre(3, 4))
        assertEquals(before + c(3, 4), drag(side, FINE))
    }

    @Test
    fun `a diagonal wobble back toward the previous letter that stops short of its middle removes nothing`() {
        // Tail (3,2), previous (2,1): the finger returns to 0.4 cell from (2,1)'s centre along the diagonal.
        val back = centre(2, 1) + Offset(0.4f, 0.4f) / 1.4142135f
        val diagonal = listOf(centre(2, 1), centre(3, 2), back, centre(4, 3))
        assertEquals(listOf(c(2, 1), c(3, 2), c(4, 3)), drag(diagonal, FINE))
    }

    @Test
    fun `a fast swipe across letters, a little off their centre line, joins every letter in order`() {
        for (off in listOf(0.2f, -0.2f, 0.3f, -0.3f, 0.4f, -0.4f)) {
            for (pitch in listOf(0.6f, 1.5f)) {
                val side = listOf(centre(3, 0) + Offset(0f, off), centre(3, 3) + Offset(0f, off))
                assertEquals("side off $off every $pitch", (0..3).map { c(3, it) }, drag(side, pitch))
                if (kotlin.math.abs(off) > 0.3f) continue
                val diagonal = listOf(centre(1, 0) + Offset(off, 0f), centre(4, 3) + Offset(off, 0f))
                assertEquals("diagonal off $off every $pitch", (0..3).map { c(1 + it, it) }, drag(diagonal, pitch))
            }
        }
    }

    private fun drag(corners: List<Offset>, pitch: Float): List<Coord> {
        val finger = polyline(corners, pitch)
        val session = StrandsDragSession(rows, cols, Coord(floor(finger[0].y).toInt(), floor(finger[0].x).toInt()), finger[0])
        var trace = Trace()
        for (point in finger.drop(1)) session.move(point, trace).forEach { trace = (it as TraceStep.Updated).trace }
        return trace.path
    }

    private fun polyline(corners: List<Offset>, pitch: Float): List<Offset> {
        val points = mutableListOf(corners.first())
        corners.zipWithNext { a, b ->
            val n = maxOf(1, ceil((b - a).getDistance() / pitch).toInt())
            (1..n).forEach { points += a + (b - a) * (it.toFloat() / n) }
        }
        return points
    }

    private fun c(row: Int, col: Int) = Coord(row, col)

    private fun centre(row: Int, col: Int) = Offset(col + 0.5f, row + 0.5f)

    private companion object {
        const val FINE = 0.05f
    }
}
