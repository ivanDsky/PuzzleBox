package com.ivandsky.puzzlebox.designsystem.chart

/**
 * What a screen reader hears for a [BarChart] of [bars]: what the bars measure ([what]), then every bar in the order
 * drawn with its label and [valueLabel]ed value, then the highest and lowest; equal bars say so once instead.
 */
fun barChartSummary(what: String, bars: List<BarChartBar>, valueLabel: (Double) -> String): String {
    if (bars.isEmpty()) return what
    fun BarChartBar.read() = "$label ${valueLabel(value)}"
    val each = bars.joinToString(". ") { it.read() }
    val highest = bars.maxBy { it.value }
    val lowest = bars.minBy { it.value }
    val range = when {
        bars.size == 1 -> ""
        highest.value == lowest.value -> " Every bar is ${valueLabel(highest.value)}."
        else -> " Highest ${highest.read()}. Lowest ${lowest.read()}."
    }
    return "$what, ${bars.size} ${if (bars.size == 1) "bar" else "bars"}. $each.$range"
}
