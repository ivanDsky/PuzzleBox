package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How tall the dial grows in its slot, and where its ring places the ticks as it grows. */
class StreakDialShapeTest {

    @Test
    fun `a slot wider than tall keeps the dial a circle as tall as the slot`() {
        val size = dialSize(maxWidth = 836.dp, maxHeight = 400.dp)

        assertEquals(384.dp, size.height)
        assertEquals(384.dp, size.width)
    }

    @Test
    fun `a wide slot caps the circle at the dial's maximum`() {
        val size = dialSize(maxWidth = 836.dp, maxHeight = 700.dp)

        assertEquals(404.dp, size.width)
        assertEquals(404.dp, size.height)
    }

    @Test
    fun `a tall narrow slot stretches the dial down to use its height, keeping a breath clear above and below`() {
        val size = dialSize(maxWidth = 380.dp, maxHeight = 520.dp)

        assertEquals(364.dp, size.width)
        assertEquals(472.dp, size.height)
    }

    @Test
    fun `on any phone the dial fills the slot's height bar the inset and breath, so no band beyond a chip is left`() {
        // The golden phone's slot, a 360x800 phone's and a 448x998 phone's, each between the month label and the chips.
        listOf(379.dp to 668.dp, 328.dp to 600.dp, 416.dp to 780.dp).forEach { (w, h) ->
            val size = dialSize(maxWidth = w, maxHeight = h)

            assertEquals(h - 48.dp, size.height)
            assertTrue("band ${h - size.height} over a chip", (h - size.height) / 2 <= 64.dp)
        }
    }

    @Test
    fun `the stretch stops at the ceiling for an extreme slot`() {
        val size = dialSize(maxWidth = 380.dp, maxHeight = 1500.dp)

        assertEquals(364.dp, size.width)
        assertEquals(364.dp * 2.6f, size.height)
    }

    @Test
    fun `the dial is continuous where a wide slot turns taller than wide`() {
        val square = dialSize(maxWidth = 500.dp, maxHeight = 500.dp)
        val justTaller = dialSize(maxWidth = 500.dp, maxHeight = 500.1.dp)
        val taller = dialSize(maxWidth = 500.dp, maxHeight = 540.dp)

        assertEquals(404.dp, square.height)
        assertEquals(square.height.value, justTaller.height.value, 0.2f)
        assertEquals(404.dp, justTaller.width)
        // It grows by what the slot gained, not by the slot's whole height.
        assertEquals(412.dp, taller.height)
        // Just over the square the breath still holds it a circle.
        assertEquals(404.dp, dialSize(maxWidth = 500.dp, maxHeight = 520.dp).height)
    }

    @Test
    fun `a slot with no room gives no dial`() {
        assertEquals(DpSize(0.dp, 0.dp), dialSize(maxWidth = 0.dp, maxHeight = 0.dp))
        assertEquals(DpSize(0.dp, 0.dp), dialSize(maxWidth = 10.dp, maxHeight = 10.dp))
        assertEquals(DpSize(0.dp, 0.dp), dialSize(maxWidth = (-5).dp, maxHeight = (-5).dp))
        assertEquals(DpSize(0.dp, 0.dp), dialSize(maxWidth = 20.dp, maxHeight = 0.dp))
    }

    @Test
    fun `a square slot stays a circle`() {
        val size = dialSize(maxWidth = 300.dp, maxHeight = 300.dp)

        assertEquals(size.width, size.height)
    }

    @Test
    fun `with no straight run the track is the circle the dial always drew`() {
        val top = DialTrack(radius = 100f, straight = 0f).at(0.0)
        val right = DialTrack(radius = 100f, straight = 0f).at(0.25)

        assertEquals(0f, top.x, EPS)
        assertEquals(-100f, top.y, EPS)
        assertEquals(0f, top.nx, EPS)
        assertEquals(-1f, top.ny, EPS)
        assertEquals(100f, right.x, EPS)
        assertEquals(0f, right.y, EPS)
        assertEquals(1f, right.nx, EPS)
        assertEquals(0f, right.ny, EPS)
    }

    @Test
    fun `a stretched track starts at the top centre and runs clockwise down the right side`() {
        val track = DialTrack(radius = 100f, straight = 200f)

        val top = track.at(0.0)
        assertEquals(0f, top.x, EPS)
        assertEquals(-200f, top.y, EPS)

        // The quarter arc ends where the right side starts, 100 above the centre line's top end.
        val quarterArc = Math.PI / 2 * 100 / track.perimeter
        val rightTop = track.at(quarterArc)
        assertEquals(100f, rightTop.x, EPS)
        assertEquals(-100f, rightTop.y, EPS)

        // Halfway down the straight run: the right edge, level with the centre, facing outward.
        val rightMid = track.at(quarterArc + 100.0 / track.perimeter)
        assertEquals(100f, rightMid.x, EPS)
        assertEquals(0f, rightMid.y, EPS)
        assertEquals(1f, rightMid.nx, EPS)
        assertEquals(0f, rightMid.ny, EPS)

        // Half the perimeter is the bottom centre.
        val bottom = track.at(0.5)
        assertEquals(0f, bottom.x, EPS)
        assertEquals(200f, bottom.y, EPS)
        assertEquals(1f, bottom.ny, EPS)
    }

    @Test
    fun `the stretched track is closed and its normals are unit`() {
        val track = DialTrack(radius = 100f, straight = 150f)
        val n = 40
        val points = (0..n).map { track.at(it / n.toDouble()) }

        points.forEach { assertEquals(1f, hypot(it.nx, it.ny), EPS) }
        // Closed: the last point is the first.
        assertEquals(points.first().x, points.last().x, EPS)
        assertEquals(points.first().y, points.last().y, EPS)
    }

    @Test
    fun `a fraction past a full trip or before the start wraps round`() {
        val track = DialTrack(radius = 100f, straight = 150f)

        listOf(1.25 to 0.25, 2.1 to 0.1, -0.25 to 0.75, -1.4 to 0.6).forEach { (wrapped, plain) ->
            val a = track.at(wrapped)
            val b = track.at(plain)
            assertEquals(b.x, a.x, EPS)
            assertEquals(b.y, a.y, EPS)
            assertEquals(b.nx, a.nx, EPS)
            assertEquals(b.ny, a.ny, EPS)
        }
    }

    @Test
    fun `the perimeter is two straight runs and a full circle`() {
        val track = DialTrack(radius = 100f, straight = 150f)

        assertEquals((2 * 150 + 2 * Math.PI * 100).toFloat(), track.perimeter, EPS)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
