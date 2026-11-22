package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.ivandsky.puzzlebox.designsystem.chart.LineChart
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * A [LineChart] with the page's motion: the line draws in on arrival (M04) and morphs to new data when the Since or
 * game filter changes (M05), see [morphLine] for how two lines of different length tween. Reduced motion fades the
 * new line in instead (M08). A line that was already drawn once (scrolled away and back) is not drawn in again, and
 * with no [StatsMotion] provided this is the plain chart.
 */
@Composable
internal fun StatsLineChart(
    points: List<LineChartPoint>,
    what: String,
    valueLabel: (Double) -> String,
    key: Any,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Double>? = null,
) {
    val stats = LocalStatsMotion.current
    if (stats == null) {
        LineChart(points, what, valueLabel, modifier, valueRange)
        return
    }
    val reduced = rememberReducedMotion()
    val settled = entranceSettled()
    val motion = remember { LineChartMotion(points, valueRange, animateArrival = !reduced && !settled && stats.claimArrival(key)) }
    LaunchedEffect(points, valueRange, reduced) { motion.show(points, valueRange, reduced) }
    StatsLineChartAt(motion.run, { motion.clock.ms }, what, valueLabel, modifier)
}

/** The chart of [run] at [elapsedMs] into it: stateless, so a preview or a test can draw any instant. */
@Composable
internal fun StatsLineChartAt(
    run: LineRun,
    elapsedMs: () -> Float,
    what: String,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    LineChart(
        points = run.pointsAt(if (run.movesPoints) elapsedMs() else 0f),
        what = what,
        valueLabel = valueLabel,
        modifier = modifier.graphicsLayer { alpha = run.alphaAt(elapsedMs()) },
        valueRange = run.scale,
        drawProgress = { run.drawAt(elapsedMs()) },
        labelAlpha = { run.labelsAt(elapsedMs()) },
    )
}
