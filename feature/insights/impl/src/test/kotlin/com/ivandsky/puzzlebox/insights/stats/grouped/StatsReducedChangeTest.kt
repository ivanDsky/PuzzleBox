package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A change of data under reduced motion swaps the chart at once: the new data from the first frame, never an empty chart fading in. */
class StatsReducedChangeTest {

    private val a = listOf(LineChartPoint(0.0, 10.0, "Nov 1"), LineChartPoint(1.0, 20.0, "Nov 2"))
    private val b = listOf(LineChartPoint(0.0, 100.0, "Oct 1"), LineChartPoint(1.0, 140.0, "Oct 2"))

    @Test
    fun `a line change under reduced motion shows the new line whole at once`() = runBlocking {
        val motion = LineChartMotion(a, null, animateArrival = false)
        motion.show(b, null, reduced = true)
        val run = motion.run
        assertEquals(b, run.pointsAt(0f))
        assertEquals(1f, run.drawAt(0f), 0f)
        assertEquals(1f, run.labelsAt(0f), 0f)
        assertEquals(0, run.totalMs)
        assertEquals(0f, motion.clock.ms, 0f)
    }

    @Test
    fun `a bar change under reduced motion shows the new bars at full height at once`() = runBlocking {
        val motion = BarChartMotion(listOf(BarChartBar("Mon", 5.0)), animateArrival = false)
        val next = listOf(BarChartBar("Mon", 9.0), BarChartBar("Tue", 3.0))
        motion.show(next, reduced = true)
        val run = motion.run
        assertEquals(next, run.bars)
        assertTrue((0..1).all { run.scaleAt(it, 0f) == 1f })
        assertEquals(1f, run.valuesAt(0f), 0f)
    }
}
