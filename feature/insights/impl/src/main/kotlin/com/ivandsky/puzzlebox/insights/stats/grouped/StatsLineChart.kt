package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.chart.LineChart
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * A [LineChart] with the page's motion: the line draws in on arrival (M04) and morphs to new data when the Since or
 * game filter changes (M05), see [morphLine] for how two lines of different length tween. Reduced motion swaps the
 * new line in at once (M08). A line that was already drawn once (scrolled away and back) is not drawn in again, and
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
    StatsLineChartAt({ motion.run }, { motion.clock.ms }, what, valueLabel, modifier)
}

/**
 * The chart of [run] at [elapsedMs] into it: stateless, so a preview or a test can draw any instant. [run] is read where
 * it is drawn, not only where it is composed: a new run and the clock restarted for it land before the next
 * recomposition, and the old run read at the restarted clock would draw an empty chart for that frame.
 */
@Composable
internal fun StatsLineChartAt(
    run: () -> LineRun,
    elapsedMs: () -> Float,
    what: String,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val composed = run()
    val currentRun by rememberUpdatedState(run)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    // Which dataset the labels are written from changes once per morph, so these only recompose then, not every frame.
    val labelPoints by remember { derivedStateOf { currentRun().labelPointsAt(currentElapsed()) } }
    val labelScale by remember { derivedStateOf { currentRun().labelScaleAt(currentElapsed()) } }
    LineChart(
        points = composed.pointsAt(if (composed.movesPoints) elapsedMs() else 0f),
        what = what,
        valueLabel = valueLabel,
        modifier = modifier,
        valueRange = composed.scale,
        drawProgress = { run().drawAt(elapsedMs()) },
        labelAlpha = { run().labelsAt(elapsedMs()) },
        dotAlpha = { run().dotsAt(elapsedMs()) },
        labelPoints = labelPoints,
        labelRange = labelScale,
        summaryPoints = composed.points,
    )
}
