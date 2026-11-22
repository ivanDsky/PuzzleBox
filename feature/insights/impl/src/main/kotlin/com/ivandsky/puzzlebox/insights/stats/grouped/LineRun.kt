package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint

/**
 * One run of a line chart's motion, read at [elapsedMs] since it started. [points] and [scale] are where the run ends
 * (the chart's data), the `...At` functions are what to draw at an instant of it.
 */
internal sealed interface LineRun {
    val points: List<LineChartPoint>
    val scale: ClosedFloatingPointRange<Double>
    val totalMs: Int

    /** True when the drawn points change over the run, so a reader must follow the clock in composition. */
    val movesPoints: Boolean get() = false

    fun pointsAt(elapsedMs: Float): List<LineChartPoint> = points
    fun drawAt(elapsedMs: Float): Float = 1f
    fun labelsAt(elapsedMs: Float): Float = 1f

    /** Nothing moves: the first sight of a chart whose arrival is skipped, and every change under reduced motion (an instant swap). */
    class Rest(override val points: List<LineChartPoint>, override val scale: ClosedFloatingPointRange<Double>) : LineRun {
        override val totalMs = 0
    }

    /** M04: the line draws in from its start, then the labels and dots fade in. */
    class Arrival(override val points: List<LineChartPoint>, override val scale: ClosedFloatingPointRange<Double>) : LineRun {
        override val totalMs = LINE_DRAW_MS
        override fun drawAt(elapsedMs: Float) = lineDrawProgress(elapsedMs)
        override fun labelsAt(elapsedMs: Float) = labelAlpha(elapsedMs, totalMs.toFloat())
    }

    /** M05: the line on screen ([from], on [fromScale]) morphs into [points]; the labels swap at the midpoint. */
    class Morph(
        private val from: List<LineChartPoint>,
        private val fromScale: ClosedFloatingPointRange<Double>,
        override val points: List<LineChartPoint>,
        override val scale: ClosedFloatingPointRange<Double>,
    ) : LineRun {
        override val totalMs = CHART_MORPH_MS
        override val movesPoints = true
        override fun pointsAt(elapsedMs: Float) = morphLine(from, fromScale, points, scale, morphProgress(elapsedMs))
        override fun labelsAt(elapsedMs: Float) = labelSwapAlpha(elapsedMs, totalMs.toFloat())
    }
}
