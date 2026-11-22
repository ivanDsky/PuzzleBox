package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The runs read a chart's motion at any instant: draw-in and growth on arrival, morph on a change, a plain fade when reduced. */
class StatsChartRunTest {

    private val a = listOf(LineChartPoint(0.0, 10.0, "Nov 1"), LineChartPoint(1.0, 20.0, "Nov 2"), LineChartPoint(2.0, 15.0, "Nov 3"))
    private val b = listOf(LineChartPoint(0.0, 100.0, "Oct 1"), LineChartPoint(1.0, 140.0, "Oct 2"))
    private val scaleA = 10.0..20.0
    private val scaleB = 100.0..140.0

    @Test
    fun `a line arrival draws in from nothing to all of it, labels last`() {
        val run = LineRun.Arrival(a, scaleA)
        assertEquals(LINE_DRAW_MS, run.totalMs)
        assertEquals(0f, run.drawAt(0f), 0f)
        assertEquals(0f, run.labelsAt(0f), 0f)
        assertTrue(run.drawAt(100f) in 0.01f..0.99f)
        assertEquals(0f, run.labelsAt(100f), 0f)
        assertEquals(1f, run.drawAt(run.totalMs.toFloat()), 0f)
        assertEquals(1f, run.labelsAt(run.totalMs.toFloat()), 0f)
        assertSame(a, run.pointsAt(100f))
    }

    @Test
    fun `a morph holds the old line at the first instant and swaps the labels`() {
        val old = listOf(LineChartPoint(0.0, 10.0, "Nov 1"), LineChartPoint(1.0, 20.0, "Nov 2"), LineChartPoint(2.0, 15.0, "Nov 3"))
        val new = listOf(LineChartPoint(0.0, 12.0, "Oct 1"), LineChartPoint(1.0, 18.0, "Oct 2"), LineChartPoint(2.0, 11.0, "Oct 3"))
        val run = LineRun.Morph(old, 10.0..20.0, new, 10.0..20.0)
        assertEquals(old.map { it.x / 2 to it.y }, run.pointsAt(0f).map { it.x to it.y })
        assertEquals(1f, run.labelsAt(0f), 0f)
        assertEquals(0f, run.labelsAt(CHART_MORPH_MS / 2f), 0.07f)
        assertEquals(1f, run.labelsAt(CHART_MORPH_MS.toFloat()), 0f)
    }

    @Test
    fun `a line morph starts on the old shape and ends on the new line itself`() {
        val run = LineRun.Morph(a, scaleA, b, scaleB)
        assertEquals(CHART_MORPH_MS, run.totalMs)
        assertEquals(scaleB, run.scale)
        val start = run.pointsAt(0f)
        assertEquals(listOf(0.0, 0.5, 1.0), start.map { it.x })
        assertEquals(100.0, start.first().y, 1e-9)
        assertEquals(140.0, start[1].y, 1e-9) // the old peak, on the new scale
        assertSame(b, run.pointsAt(run.totalMs.toFloat()))
        assertEquals(1f, run.drawAt(0f), 0f)
        assertEquals(1f, run.labelsAt(run.totalMs.toFloat()), 0f)
    }

    private val week = listOf(BarChartBar("Mon", 100.0), BarChartBar("Tue", 50.0), BarChartBar("Wed", 75.0))

    @Test
    fun `bars arrive one after another and their values come last`() {
        val run = BarRun.Arrival(week)
        assertEquals(barGrowthTotalMs(3), run.totalMs)
        assertEquals(0f, run.scaleAt(0, 0f), 0f)
        assertEquals(0f, run.scaleAt(2, 40f), 0f)
        assertTrue(run.scaleAt(0, 100f) > run.scaleAt(1, 100f))
        assertEquals(listOf(1f, 1f, 1f), (0..2).map { run.scaleAt(it, run.totalMs.toFloat()) })
        assertEquals(0f, run.valuesAt(0f), 0f)
        assertEquals(1f, run.valuesAt(run.totalMs.toFloat()), 0f)
    }

    @Test
    fun `bars morph from the heights on screen and a mid-morph change carries on from them`() {
        val next = listOf(BarChartBar("Mon", 40.0), BarChartBar("Tue", 80.0), BarChartBar("Wed", 0.0))
        val morph = BarRun.Morph(barFractions(week), next)
        assertEquals(listOf(2f, 0.5f), (0..1).map { morph.scaleAt(it, 0f) })
        assertEquals(listOf(1f, 1f, 1f), (0..2).map { morph.scaleAt(it, morph.totalMs.toFloat()) })
        // Drawn heights, as fractions of the tallest of the new bars, are what a next morph starts from.
        val shown = morph.shownFractions(0f)
        assertEquals(1f, shown.getValue("Mon"), 1e-6f)
        assertEquals(0.5f, shown.getValue("Tue"), 1e-6f)
        assertEquals(0f, shown.getValue("Wed"), 0f)
    }

    @Test
    fun `a bar morph holds the old bars at the first instant and swaps the values`() {
        val next = listOf(BarChartBar("Mon", 40.0), BarChartBar("Tue", 80.0), BarChartBar("Wed", 75.0))
        val morph = BarRun.Morph(barFractions(week), next)
        val before = barFractions(week)
        val fractions = barFractions(next)
        (0..2).forEach { assertEquals(before.getValue(next[it].label), fractions.getValue(next[it].label) * morph.scaleAt(it, 0f), 1e-6f) }
        assertEquals(1f, morph.valuesAt(0f), 0f)
        assertEquals(0f, morph.valuesAt(CHART_MORPH_MS / 2f), 0.07f)
        assertEquals(1f, morph.valuesAt(morph.totalMs.toFloat()), 0f)
    }
}
