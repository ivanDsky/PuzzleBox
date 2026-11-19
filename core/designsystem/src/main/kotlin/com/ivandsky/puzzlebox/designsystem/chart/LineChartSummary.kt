package com.ivandsky.puzzlebox.designsystem.chart

/**
 * What a screen reader hears for a [LineChart] of [points]: what the line measures ([what]), then its first and last
 * point and its lowest and highest, each with its label and [valueLabel]ed value; a flat line says so once. The points
 * are read in time order, whatever order they come in.
 */
fun lineChartSummary(what: String, points: List<LineChartPoint>, valueLabel: (Double) -> String): String {
    if (points.isEmpty()) return what
    val ordered = points.sortedBy { it.x }
    val first = ordered.first()
    val last = ordered.last()
    val lowest = ordered.minBy { it.y }
    val highest = ordered.maxBy { it.y }
    fun LineChartPoint.read() = "$label ${valueLabel(y)}"
    val range = if (lowest.y == highest.y) {
        "Every point is ${valueLabel(lowest.y)}."
    } else {
        "Lowest ${lowest.read()}. Highest ${highest.read()}."
    }
    return "$what, ${ordered.size} points. First ${first.read()}. Last ${last.read()}. $range"
}
