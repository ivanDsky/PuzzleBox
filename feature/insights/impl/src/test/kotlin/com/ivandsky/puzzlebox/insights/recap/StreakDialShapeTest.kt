package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import org.junit.Assert.assertEquals
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
    fun `a tall narrow slot stretches the dial down to use its height`() {
        val size = dialSize(maxWidth = 380.dp, maxHeight = 520.dp)

        assertEquals(364.dp, size.width)
        assertEquals(504.dp, size.height)
    }

    @Test
    fun `the stretch stops at the elongation cap so the ring stays a dial`() {
        val size = dialSize(maxWidth = 380.dp, maxHeight = 800.dp)

        assertEquals(364.dp, size.width)
        assertEquals(364.dp * 1.8f, size.height)
    }

    @Test
    fun `a square slot stays a circle`() {
        val size = dialSize(maxWidth = 300.dp, maxHeight = 300.dp)

        assertEquals(size.width, size.height)
    }

    @Test
    fun `with no straight run the track is the circle the dial always drew`() {
        val top = DialTrack(radius = 100f, straight = 0f).at(0f)
        val right = DialTrack(radius = 100f, straight = 0f).at(0.25f)

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

        val top = track.at(0f)
        assertEquals(0f, top.x, EPS)
        assertEquals(-200f, top.y, EPS)

        // The quarter arc ends where the right side starts, 100 above the centre line's top end.
        val quarterArc = (Math.PI / 2 * 100).toFloat() / track.perimeter
        val rightTop = track.at(quarterArc)
        assertEquals(100f, rightTop.x, EPS)
        assertEquals(-100f, rightTop.y, EPS)

        // Halfway down the straight run: the right edge, level with the centre, facing outward.
        val rightMid = track.at(quarterArc + 100f / track.perimeter)
        assertEquals(100f, rightMid.x, EPS)
        assertEquals(0f, rightMid.y, EPS)
        assertEquals(1f, rightMid.nx, EPS)
        assertEquals(0f, rightMid.ny, EPS)

        // Half the perimeter is the bottom centre.
        val bottom = track.at(0.5f)
        assertEquals(0f, bottom.x, EPS)
        assertEquals(200f, bottom.y, EPS)
        assertEquals(1f, bottom.ny, EPS)
    }

    @Test
    fun `the stretched track is closed and its normals are unit`() {
        val track = DialTrack(radius = 100f, straight = 150f)
        val n = 40
        val points = (0..n).map { track.at(it / n.toFloat()) }

        points.forEach { assertEquals(1f, hypot(it.nx, it.ny), EPS) }
        // Closed: the last point is the first.
        assertEquals(points.first().x, points.last().x, EPS)
        assertEquals(points.first().y, points.last().y, EPS)
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
