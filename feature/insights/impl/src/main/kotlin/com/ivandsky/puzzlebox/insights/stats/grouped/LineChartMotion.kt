package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint

/**
 * The motion state of one line chart: the [run] on screen and the [clock] it is read at. A chart that starts with
 * [animateArrival] draws in; after that, new data morphs from what is drawn at that moment (so a change that lands
 * mid-morph carries on from the line as it then looks) or, under reduced motion, swaps at once (M08: no movement).
 */
internal class LineChartMotion(
    points: List<LineChartPoint>,
    valueRange: ClosedFloatingPointRange<Double>?,
    animateArrival: Boolean,
) {
    val clock = ChartClock()
    var run: LineRun by mutableStateOf(
        lineScale(points, valueRange).let { scale -> if (animateArrival) LineRun.Arrival(points, scale) else LineRun.Rest(points, scale) },
    )
        private set

    /** Shows [points]: carries on with the run on screen when it is for the same data, starts the next one otherwise. */
    suspend fun show(points: List<LineChartPoint>, valueRange: ClosedFloatingPointRange<Double>?, reduced: Boolean) {
        if (points.size < 2) {
            run = LineRun.Rest(points, valueRange ?: 0.0..0.0)
            return
        }
        val scale = lineScale(points, valueRange)
        if (points != run.points || scale != run.scale) {
            val shown = run.pointsAt(clock.ms)
            run = when {
                reduced -> LineRun.Rest(points, scale)
                shown.size < 2 -> LineRun.Arrival(points, scale)
                else -> LineRun.Morph(shown, run.scale, points, scale)
            }
            clock.restart()
        }
        clock.playTo(run.totalMs)
    }
}
