package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chart motion clock: every curve is a pure function of the milliseconds since the chart started moving. */
class StatsChartTimingTest {

    @Test
    fun `the line draws in over 400ms and the morph takes the chart morph duration`() {
        assertEquals(0f, lineDrawProgress(0f), 0f)
        assertEquals(1f, lineDrawProgress(MotionTokens.DurationExpressive.toFloat()), 0f)
        assertEquals(1f, lineDrawProgress(900f), 0f)
        assertEquals(400, LINE_DRAW_MS)
        assertEquals(300, CHART_MORPH_MS)
        assertEquals(0f, morphProgress(0f), 0f)
        assertEquals(1f, morphProgress(CHART_MORPH_MS.toFloat()), 0f)
    }

    @Test
    fun `progress only moves forward`() {
        val samples = (0..40).map { lineDrawProgress(it * 10f) }
        assertEquals(samples.sorted(), samples)
        assertTrue(lineDrawProgress(100f) in 0.01f..0.99f)
    }

    @Test
    fun `labels stay hidden until the last chart label fade of the run, then fade in`() {
        val total = MotionTokens.ChartMorphDuration.toFloat()
        val fade = MotionTokens.ChartLabelFade.toFloat()
        assertEquals(0f, labelAlpha(0f, total), 0f)
        assertEquals(0f, labelAlpha(total - fade, total), 0f)
        assertEquals(0.5f, labelAlpha(total - fade / 2, total), 1e-6f)
        assertEquals(1f, labelAlpha(total, total), 0f)
    }

    @Test
    fun `bars grow one after another 30ms apart over the long duration`() {
        assertEquals(30, BAR_STAGGER_MS)
        assertEquals(0f, barGrowth(index = 2, elapsedMs = 60f), 0f)
        assertEquals(1f, barGrowth(index = 2, elapsedMs = 60f + MotionTokens.DurationLong), 0f)
        assertTrue(barGrowth(index = 0, elapsedMs = 100f) > barGrowth(index = 1, elapsedMs = 100f))
        assertEquals(300 + 30 * 6, barGrowthTotalMs(bars = 7))
        assertEquals(300, barGrowthTotalMs(bars = 1))
    }

    @Test
    fun `a morph starts every bar at its old height and ends at its own`() {
        val old = listOf(BarChartBar("Mon", 100.0), BarChartBar("Tue", 50.0), BarChartBar("Wed", 100.0))
        val new = listOf(BarChartBar("Mon", 40.0), BarChartBar("Tue", 80.0), BarChartBar("Thu", 80.0))
        // Old fractions of the tallest: Mon 1, Tue .5; new: Mon .5, Tue 1, Thu 1.
        val start = barMorphStarts(barFractions(old), new)
        assertEquals(listOf(2f, 0.5f, 0f), start)
        assertEquals(listOf(2f, 0.5f, 0f), (0..2).map { barMorphScale(start[it], 0f) })
        assertEquals(listOf(1f, 1f, 1f), (0..2).map { barMorphScale(start[it], CHART_MORPH_MS.toFloat()) })
    }

    @Test
    fun `bar fractions are of the tallest bar and never negative`() {
        assertEquals(mapOf("a" to 1f, "b" to 0.25f, "c" to 0f), barFractions(listOf(BarChartBar("a", 8.0), BarChartBar("b", 2.0), BarChartBar("c", -3.0))))
        assertEquals(mapOf("a" to 0f), barFractions(listOf(BarChartBar("a", 0.0))))
    }
}
