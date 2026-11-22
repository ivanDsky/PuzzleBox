package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A morph writes the old data's labels until its midpoint and the new data's after, and keeps the dots hidden until it has ended. */
class StatsMorphLabelsTest {

    private val old = listOf(LineChartPoint(0.0, 10.0, "Nov 1"), LineChartPoint(1.0, 20.0, "Nov 2"), LineChartPoint(2.0, 15.0, "Nov 3"))
    private val new = listOf(LineChartPoint(0.0, 100.0, "Oct 1"), LineChartPoint(1.0, 140.0, "Oct 2"))
    private val line = LineRun.Morph(old, 10.0..20.0, new, 100.0..140.0)
    private val half = CHART_MORPH_MS / 2f

    @Test
    fun `a line morph writes the old end labels and axis values before the midpoint`() {
        listOf(0f, 8f, 16f, half - 1f).forEach {
            assertEquals("labels at $it", old, line.labelPointsAt(it))
            assertEquals("axis at $it", 10.0..20.0, line.labelScaleAt(it))
        }
    }

    @Test
    fun `a line morph writes the new end labels and axis values from the midpoint`() {
        listOf(half, CHART_MORPH_MS - 1f, CHART_MORPH_MS.toFloat()).forEach {
            assertEquals("labels at $it", new, line.labelPointsAt(it))
            assertEquals("axis at $it", 100.0..140.0, line.labelScaleAt(it))
        }
    }

    @Test
    fun `the dots stay hidden through a line morph and fade in over its last label fade`() {
        val fadeStart = (CHART_MORPH_MS - MotionTokens.ChartLabelFade).toFloat()
        listOf(0f, 8f, 16f, 100f, fadeStart).forEach { assertEquals("dots at $it", 0f, line.dotsAt(it), 0f) }
        assertEquals(0.5f, line.dotsAt(fadeStart + MotionTokens.ChartLabelFade / 2f), 0.001f)
        assertEquals(1f, line.dotsAt(CHART_MORPH_MS.toFloat()), 0f)
    }

    @Test
    fun `a line that does not morph writes its own labels and its dots follow its labels`() {
        val arrival = LineRun.Arrival(old, 10.0..20.0)
        assertEquals(old, arrival.labelPointsAt(0f))
        assertEquals(10.0..20.0, arrival.labelScaleAt(0f))
        listOf(0f, 100f, arrival.totalMs.toFloat()).forEach { assertEquals(arrival.labelsAt(it), arrival.dotsAt(it), 0f) }
    }

    @Test
    fun `bars write the old values before the midpoint, none for a bar without an old partner, and the new after`() {
        val before = listOf(BarChartBar("Mon", 95.0), BarChartBar("Tue", 88.0))
        val after = listOf(BarChartBar("Mon", 110.0), BarChartBar("Tue", 80.0), BarChartBar("Wed", 70.0))
        val run = BarRun.Morph(barFractions(before), after, before)
        listOf(0f, 8f, 16f, half - 1f).forEach {
            assertEquals("Mon at $it", 95.0, run.valueAt(0, it)!!, 0.0)
            assertEquals("Tue at $it", 88.0, run.valueAt(1, it)!!, 0.0)
            assertNull("Wed has no old value at $it", run.valueAt(2, it))
        }
        listOf(half, CHART_MORPH_MS.toFloat()).forEach {
            assertEquals(listOf(110.0, 80.0, 70.0), (0..2).map { i -> run.valueAt(i, it) })
        }
    }

    @Test
    fun `bars that do not morph write their own values`() {
        val bars = listOf(BarChartBar("Mon", 95.0), BarChartBar("Tue", 88.0))
        assertEquals(95.0, BarRun.Rest(bars).valueAt(0, 0f)!!, 0.0)
        assertEquals(88.0, BarRun.Arrival(bars).valueAt(1, 0f)!!, 0.0)
    }
}
