package com.ivandsky.puzzlebox.designsystem.chart

/**
 * One bar of a [BarChart]: [label] names it under the bar and for a screen reader ("Mon"), [value] sets its height
 * (never below 0).
 */
data class BarChartBar(val label: String, val value: Double)
