package com.ivandsky.puzzlebox.designsystem.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class LineChartSummaryTest {

    private val seconds: (Double) -> String = { "${it.toInt()}s" }

    @Test
    fun `reads first, last, lowest and highest in time order`() {
        val points = listOf(
            LineChartPoint(14.0, 90.0, "Nov 9"),
            LineChartPoint(0.0, 60.0, "Oct 26"),
            LineChartPoint(7.0, 120.0, "Nov 2"),
            LineChartPoint(21.0, 80.0, "Nov 16"),
        )

        assertEquals(
            "Average solve time, 4 points. First Oct 26 60s. Last Nov 16 80s. Lowest Oct 26 60s. Highest Nov 2 120s.",
            lineChartSummary("Average solve time", points, seconds),
        )
    }

    @Test
    fun `a flat line says every point once`() {
        val points = listOf(LineChartPoint(0.0, 45.0, "Nov 2"), LineChartPoint(7.0, 45.0, "Nov 9"))

        assertEquals(
            "Average solve time, 2 points. First Nov 2 45s. Last Nov 9 45s. Every point is 45s.",
            lineChartSummary("Average solve time", points, seconds),
        )
    }

    @Test
    fun `no points is just what it measures`() {
        assertEquals("Average solve time", lineChartSummary("Average solve time", emptyList(), seconds))
    }
}
