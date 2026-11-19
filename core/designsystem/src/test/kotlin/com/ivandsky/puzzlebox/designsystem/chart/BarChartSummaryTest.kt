package com.ivandsky.puzzlebox.designsystem.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class BarChartSummaryTest {

    private val seconds: (Double) -> String = { "${it.toInt()}s" }

    @Test
    fun `reads every bar in the order drawn, then the highest and lowest`() {
        val bars = listOf(BarChartBar("Mon", 90.0), BarChartBar("Wed", 120.0), BarChartBar("Sun", 60.0))

        assertEquals(
            "Average solve time by weekday, 3 bars. Mon 90s. Wed 120s. Sun 60s. Highest Wed 120s. Lowest Sun 60s.",
            barChartSummary("Average solve time by weekday", bars, seconds),
        )
    }

    @Test
    fun `equal bars say so once`() {
        val bars = listOf(BarChartBar("Mon", 45.0), BarChartBar("Tue", 45.0))

        assertEquals(
            "Average solve time by weekday, 2 bars. Mon 45s. Tue 45s. Every bar is 45s.",
            barChartSummary("Average solve time by weekday", bars, seconds),
        )
    }

    @Test
    fun `one bar has no highest or lowest`() {
        assertEquals(
            "Average solve time by weekday, 1 bar. Tue 45s.",
            barChartSummary("Average solve time by weekday", listOf(BarChartBar("Tue", 45.0)), seconds),
        )
    }

    @Test
    fun `no bars is just what they measure`() {
        assertEquals("Average solve time", barChartSummary("Average solve time", emptyList(), seconds))
    }
}
