package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.MotionTokens
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

    /** The dots' opacity: the labels' unless a run says otherwise. */
    fun dotsAt(elapsedMs: Float): Float = labelsAt(elapsedMs)

    /** The points whose end labels are written at [elapsedMs], and the scale whose values are written at the rules. */
    fun labelPointsAt(elapsedMs: Float): List<LineChartPoint> = points
    fun labelScaleAt(elapsedMs: Float): ClosedFloatingPointRange<Double> = scale

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

    /**
     * M05: the line on screen ([from], on [fromScale]) morphs into [points]. The labels are the old line's until the
     * midpoint (fading out over [MotionTokens.ChartLabelFade]) and the new one's after it (fading in over the last of
     * the run); the dots are hidden until the last [MotionTokens.ChartLabelFade] of the run and fade in over it, since before that they would sit on the resampled grid.
     */
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
        override fun dotsAt(elapsedMs: Float) = ((elapsedMs - (totalMs - MotionTokens.ChartLabelFade)) / MotionTokens.ChartLabelFade).coerceIn(0f, 1f)
        override fun labelPointsAt(elapsedMs: Float) = if (labelsAreOld(elapsedMs, totalMs.toFloat())) from else points
        override fun labelScaleAt(elapsedMs: Float) = if (labelsAreOld(elapsedMs, totalMs.toFloat())) fromScale else scale
    }
}
