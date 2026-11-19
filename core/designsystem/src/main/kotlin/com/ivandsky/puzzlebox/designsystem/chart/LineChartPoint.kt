package com.ivandsky.puzzlebox.designsystem.chart

/**
 * One point of a [LineChart]: [x] places it along the time axis (any unit, such as an epoch day, only the order and the
 * spacing matter), [y] is its value, and [label] names it for the axis and for a screen reader ("Nov 9").
 */
data class LineChartPoint(val x: Double, val y: Double, val label: String)
