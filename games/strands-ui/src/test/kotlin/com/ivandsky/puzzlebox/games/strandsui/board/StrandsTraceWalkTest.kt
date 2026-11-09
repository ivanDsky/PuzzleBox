package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A5 on the walk, driven through [StrandsDragSession] in cell units over a 6 × 8 board: a
 * drag's direction is its travel over the last [StrandsBoardSpec.TravelWindowCells], so a
 * diagonal drawn with a ±20° wobble, on or off the centre line, joins only diagonal letters,
 * and a side drag (straight or ±15°) joins the side letters. Fingers are zigzags: legs of
 * [LEG] cells alternating ± the wobble.
 */
class StrandsTraceWalkTest {

    private val rows = 8
    private val cols = 6

    @Test
    fun `a wobbly diagonal joins only the diagonal letters, on and off the centre line`() {
        val diagonals = listOf(
            Coord(1, 1) to listOf(Coord(1, 1), Coord(2, 2), Coord(3, 3), Coord(4, 4)),
            Coord(1, 4) to listOf(Coord(1, 4), Coord(2, 3), Coord(3, 2), Coord(4, 1)),
            Coord(6, 1) to listOf(Coord(6, 1), Coord(5, 2), Coord(4, 3), Coord(3, 4)),
            Coord(6, 4) to listOf(Coord(6, 4), Coord(5, 3), Coord(4, 2), Coord(3, 1)),
        )
        for ((start, expected) in diagonals) {
            val end = expected.last()
            for (offset in listOf(0f, 0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f)) {
                for (wobble in listOf(20f, -20f)) {
                    for (pitch in listOf(FINE, SPARSE)) {
                        val finger = zigzag(start, end, offset, wobble, pitch)
                        assertEquals("$start→$end off $offset wobble $wobble every $pitch", expected, drag(finger))
                    }
                }
            }
        }
    }

    @Test
    fun `a straight or slightly wobbly side drag joins the side letters`() {
        val sides = listOf(
            Coord(2, 0) to listOf(Coord(2, 0), Coord(2, 1), Coord(2, 2), Coord(2, 3), Coord(2, 4)),
            Coord(2, 5) to listOf(Coord(2, 5), Coord(2, 4), Coord(2, 3), Coord(2, 2)),
            Coord(1, 3) to listOf(Coord(1, 3), Coord(2, 3), Coord(3, 3), Coord(4, 3), Coord(5, 3)),
            Coord(7, 2) to listOf(Coord(7, 2), Coord(6, 2), Coord(5, 2), Coord(4, 2)),
        )
        for ((start, expected) in sides) {
            for (wobble in listOf(0f, 10f, -10f)) {
                for (pitch in listOf(FINE, SPARSE)) {
                    val finger = zigzag(start, expected.last(), offset = 0f, wobble, pitch)
                    assertEquals("$start→${expected.last()} wobble $wobble every $pitch", expected, drag(finger))
                }
            }
        }
    }

    @Test
    fun `an L-shaped drag turns from side to diagonal`() {
        val finger = polyline(listOf(centre(Coord(3, 0)), centre(Coord(3, 2)), centre(Coord(5, 4))), FINE)
        assertEquals(
            listOf(Coord(3, 0), Coord(3, 1), Coord(3, 2), Coord(4, 3), Coord(5, 4)),
            drag(finger),
        )
    }

    @Test
    fun `drawing back over the previous letter backs up one`() {
        val finger = polyline(listOf(centre(Coord(2, 1)), centre(Coord(2, 3)), centre(Coord(2, 2))), FINE)
        assertEquals(listOf(Coord(2, 1), Coord(2, 2)), drag(finger))
    }

    @Test
    fun `a wrong diagonal the finger never came to gives way to the side letter it then draws on`() {
        // An east drag dipping 29° takes the diagonal (4, 2) while the finger is still in (3, 2);
        // the finger never came to (4, 2), and now it draws on east through (3, 2) to (3, 3).
        val finger = polyline(listOf(centre(Coord(3, 1)), SLIP, Offset(2.6f, 3.3f), centre(Coord(3, 3))), FINE)
        assertEquals(listOf(Coord(3, 1), Coord(3, 2), Coord(3, 3)), drag(finger))
    }

    @Test
    fun `a diagonal the finger did come to stays, with the finger beside it`() {
        // The finger reaches (4, 2)'s centre, then draws on east to (4, 3): a real turn, no reroute.
        val finger = polyline(listOf(centre(Coord(3, 1)), centre(Coord(4, 2)), centre(Coord(4, 3))), FINE)
        assertEquals(listOf(Coord(3, 1), Coord(4, 2), Coord(4, 3)), drag(finger))
    }

    @Test
    fun `drawing back over an earlier letter joins nothing - the path is a wall the trace draws on from`() {
        // East along row 2, down, back west along row 3, then up onto (2, 1): traced earlier, not
        // the previous letter — the wall refuses, the U-turn stays, and the trace draws on south.
        val finger = polyline(
            listOf(
                centre(Coord(2, 0)), centre(Coord(2, 2)), centre(Coord(3, 2)), centre(Coord(3, 1)),
                centre(Coord(2, 1)), centre(Coord(4, 1)),
            ),
            FINE,
        )
        assertEquals(
            listOf(Coord(2, 0), Coord(2, 1), Coord(2, 2), Coord(3, 2), Coord(3, 1), Coord(4, 1)),
            drag(finger),
        )
    }

    @Test
    fun `backing out of a wrong diagonal recovers the side letter`() {
        val finger = polyline(listOf(centre(Coord(3, 1)), SLIP, centre(Coord(3, 3)), centre(Coord(3, 1)), centre(Coord(3, 2))), FINE)
        assertEquals(listOf(Coord(3, 1), Coord(3, 2)), drag(finger))
    }

    @Test
    fun `a side drag up to 22_5 degrees off its axis joins the side letter, not the diagonal`() {
        // F1: a heading near the cone edge must still read as the side.
        val heading = Math.toRadians(20.0).let { Offset(cos(it).toFloat(), sin(it).toFloat()) }
        val finger = polyline(listOf(centre(Coord(3, 1)), centre(Coord(3, 1)) + heading), FINE)
        assertEquals(listOf(Coord(3, 1), Coord(3, 2)), drag(finger))
    }

    @Test
    fun `a horizontal drag wobbling 15 degrees at the 48dp phone pitch never joins a diagonal`() {
        // 48dp is the phone's pitch floor, so a finger's ~4dp of lateral shake is 0.08 cell.
        val shake = 4f / 48f
        for (wobble in listOf(15f, -15f)) {
            for (offset in listOf(0f, shake, -shake)) {
                for (pitch in listOf(FINE, SPARSE)) {
                    val finger = zigzag(Coord(2, 0), Coord(2, 4), offset, wobble, pitch)
                    val expected = (0..4).map { Coord(2, it) }
                    assertEquals("wobble $wobble off $offset every $pitch", expected, drag(finger))
                }
            }
        }
    }

    @Test
    fun `a diagonal joins only 0_55 of the way along its step`() {
        // F1: the old 0.40 joined (3, 2) with the finger barely past the corner of the token.
        val start = centre(Coord(2, 1))
        val short = polyline(listOf(start, start + Offset(0.52f, 0.52f)), FINE)
        assertEquals(emptyList<Coord>(), drag(short))
        val past = polyline(listOf(start, start + Offset(0.58f, 0.58f)), FINE)
        assertEquals(listOf(Coord(2, 1), Coord(3, 2)), drag(past))
    }

    @Test
    fun `a letter the finger comes to the heart of joins even when the heading reads diagonal`() {
        // Cutting the corner from (2, 1) toward (2, 2) at 31° reads diagonal and would take (3, 2)
        // past it; the finger is 0.11 from (2, 2)'s centre, so (2, 2) joins first.
        val finger = polyline(listOf(Offset(1.9f, 2.15f), Offset(2.4f, 2.45f)), FINE)
        assertEquals(listOf(Coord(2, 1), Coord(2, 2)), drag(finger))
    }

    @Test
    fun `nothing joins close to the last centre`() {
        val start = centre(Coord(4, 2))
        val finger = polyline(listOf(start, start + Offset(0.28f, 0f), start + Offset(0f, 0.28f), start + Offset(-0.2f, -0.2f)), FINE)
        assertEquals(emptyList<Coord>(), drag(finger))
    }

    @Test
    fun `the travel is the finger's direction over the last window`() {
        val walk = StrandsTraceWalk(rows, cols, centre(Coord(3, 2)))
        val stay = { listOf(Coord(3, 2)) }
        walk.moveTo(centre(Coord(3, 2)) + Offset(0.05f, 0f), stay) {}
        assertEquals(null, walk.travel())
        walk.moveTo(centre(Coord(3, 2)) + Offset(0.2f, 0f), stay) {}
        walk.moveTo(centre(Coord(3, 2)) + Offset(0.2f, 0.2f), stay) {}
        val travel = checkNotNull(walk.travel())
        // Only the last 0.25 cell of travel counts: 0.2 straight down and the 0.05 before it.
        assertEquals(0.05f, travel.x, 1e-3f)
        assertEquals(0.2f, travel.y, 1e-3f)
    }

    /** Runs [finger] (its first point the touch-down) through the [StrandsDragSession] the gesture layer drives. */
    private fun drag(finger: List<Offset>): List<Coord> {
        val session = StrandsDragSession(rows, cols, Coord(floor(finger[0].y).toInt(), floor(finger[0].x).toInt()), finger[0])
        var trace = Trace()
        for (point in finger.drop(1)) session.move(point, trace).forEach { trace = (it as TraceStep.Updated).trace }
        return trace.path
    }

    private fun centre(at: Coord) = Offset(at.col + 0.5f, at.row + 0.5f)

    /**
     * From [from]'s centre to [to]'s, shifted [offset] cells off the centre line, heading
     * alternately [wobble]° either side of the line, then sampled every [pitch] cells.
     */
    private fun zigzag(from: Coord, to: Coord, offset: Float, wobble: Float, pitch: Float): List<Offset> {
        val a = centre(from)
        val span = centre(to) - a
        val length = span.getDistance()
        val along = span / length
        val across = Offset(-along.y, along.x)
        val shift = across * offset
        val turn = Math.toRadians(wobble.toDouble())
        val ahead = along * cos(turn).toFloat()
        val side = across * sin(turn).toFloat()
        val corners = mutableListOf(a + shift)
        var travelled = 0f
        var sign = 1
        while (true) {
            val leg = ahead + side * sign.toFloat()
            val step = minOf(LEG, (length - travelled) / ahead.getDistance())
            if (step <= 1e-4f) break
            corners += corners.last() + leg * step
            travelled += ahead.getDistance() * step
            sign = -sign
        }
        return polyline(corners, pitch)
    }

    private fun polyline(corners: List<Offset>, pitch: Float): List<Offset> {
        val points = mutableListOf(corners.first())
        corners.zipWithNext { a, b ->
            val n = maxOf(1, kotlin.math.ceil((b - a).getDistance() / pitch).toInt())
            (1..n).forEach { points += a + (b - a) * (it.toFloat() / n) }
        }
        return points
    }

    private companion object {
        const val LEG = 0.3f

        /** A slow finger: an event every 0.05 cell. */
        const val FINE = 0.05f

        /** A fast swipe: events 0.6 cell apart, which the walk fills with its own sub-steps. */
        const val SPARSE = 0.6f

        /** Where an east drag from (3, 1)'s centre, dipping south, first takes the diagonal. */
        val SLIP = Offset(2.05f, 3.8f)
    }
}
