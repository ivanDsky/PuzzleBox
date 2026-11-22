package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The line morph resamples both point sets onto one shared x grid and interpolates y between them. */
class StatsLineMorphTest {

    private fun line(vararg ys: Double, x: (Int) -> Double = { it * 7.0 }) =
        ys.mapIndexed { index, y -> LineChartPoint(x(index), y, "p$index") }

    private val d = 1e-9

    @Test
    fun `scale is the fixed range or the points own lowest and highest`() {
        assertEquals(10.0..30.0, lineScale(line(20.0, 10.0, 30.0), null))
        assertEquals(0.0..1.0, lineScale(line(0.2, 0.4), 0.0..1.0))
    }

    @Test
    fun `progress 1 is the target line itself`() {
        val to = line(5.0, 9.0, 7.0)
        assertSame(to, morphLine(line(1.0, 2.0), 1.0..2.0, to, 5.0..9.0, progress = 1f))
    }

    @Test
    fun `progress 0 draws the old shape on the new scale`() {
        val from = line(10.0, 20.0, 15.0) // fractions 0, 1, 0.5 of its own 10..20
        val to = line(0.0, 100.0, 50.0)
        val start = morphLine(from, 10.0..20.0, to, 0.0..100.0, progress = 0f)
        assertEquals(listOf(0.0, 100.0, 50.0), start.map { it.y })
    }

    @Test
    fun `the middle is halfway between the two shapes`() {
        val from = line(10.0, 20.0) // 0 .. 1
        val to = line(100.0, 100.0 + 50.0) // 0 .. 1 on 100..150
        val mid = morphLine(from, 10.0..20.0, to, 100.0..150.0, progress = 0.5f)
        assertEquals(listOf(100.0, 150.0), mid.map { it.y })
        val lopsided = morphLine(line(10.0, 10.0, 20.0), 10.0..20.0, line(100.0, 150.0, 100.0), 100.0..150.0, progress = 0.5f)
        assertEquals(listOf(100.0, 125.0, 125.0), lopsided.map { it.y }.map { Math.round(it * 1e6) / 1e6 })
    }

    @Test
    fun `different lengths share one grid holding every point of both lines`() {
        val from = line(10.0, 20.0, 10.0) // x at 0, .5, 1
        val to = line(0.0, 10.0, 20.0, 30.0, 40.0) // x at 0, .25, .5, .75, 1
        val mid = morphLine(from, 10.0..20.0, to, 0.0..40.0, progress = 0.5f)
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), mid.map { it.x })
        // The old line has no vertex at .25: it is on the way up, at 0.5 of its range there.
        val start = morphLine(from, 10.0..20.0, to, 0.0..40.0, progress = 0f)
        assertEquals(listOf(0.0, 20.0, 40.0, 20.0, 0.0), start.map { it.y })
    }

    @Test
    fun `irregular x keeps its spacing on the grid`() {
        val to = line(1.0, 2.0, 3.0, x = { doubleArrayOf(0.0, 1.0, 10.0)[it] })
        val from = line(1.0, 3.0)
        val start = morphLine(from, 1.0..3.0, to, 1.0..3.0, progress = 0f)
        assertEquals(listOf(0.0, 0.1, 1.0), start.map { it.x })
        assertEquals(1.0, start[0].y, d)
        assertEquals(1.2, start[1].y, d)
        assertEquals(3.0, start[2].y, d)
    }

    @Test
    fun `the ends carry the target's labels`() {
        val mid = morphLine(line(1.0, 2.0, 3.0), 1.0..3.0, line(4.0, 5.0), 4.0..5.0, progress = 0.4f)
        assertEquals("p0", mid.first().label)
        assertEquals("p1", mid.last().label)
    }

    @Test
    fun `a flat old or new line sits mid-way in its scale`() {
        val flatFrom = morphLine(line(30.0, 30.0), 30.0..30.0, line(0.0, 100.0), 0.0..100.0, progress = 0f)
        assertEquals(listOf(50.0, 50.0), flatFrom.map { it.y })
        val flatTo = morphLine(line(0.0, 100.0), 0.0..100.0, line(30.0, 30.0), 30.0..30.0, progress = 0.5f)
        assertEquals(listOf(30.0, 30.0), flatTo.map { it.y })
    }
}
