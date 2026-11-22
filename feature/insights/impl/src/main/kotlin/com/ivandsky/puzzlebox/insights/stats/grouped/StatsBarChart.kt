package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.chart.BarChart
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * A [BarChart] with the page's motion: the bars grow from the baseline one after another on arrival (M04) and
 * rescale to the new heights when the Since or game filter changes, a bar with no old partner growing in (M05).
 * Reduced motion swaps the new bars in at once (M08). A chart that was already drawn once (scrolled away and back)
 * does not grow again, and with no [StatsMotion] provided this is the plain chart.
 */
@Composable
internal fun StatsBarChart(
    bars: List<BarChartBar>,
    what: String,
    valueLabel: (Double) -> String,
    key: Any,
    modifier: Modifier = Modifier,
) {
    val stats = LocalStatsMotion.current
    if (stats == null) {
        BarChart(bars, what, valueLabel, modifier)
        return
    }
    val reduced = rememberReducedMotion()
    val settled = entranceSettled()
    val motion = remember { BarChartMotion(bars, animateArrival = !reduced && !settled && stats.claimArrival(key)) }
    LaunchedEffect(bars, reduced) { motion.show(bars, reduced) }
    StatsBarChartAt({ motion.run }, { motion.clock.ms }, what, valueLabel, modifier)
}

/**
 * The chart of [run] at [elapsedMs] into it: stateless, so a preview or a test can draw any instant. [run] is read where
 * it is drawn, not only where it is composed: a new run and the clock restarted for it land before the next
 * recomposition, and the old run read at the restarted clock would draw an empty chart for that frame.
 */
@Composable
internal fun StatsBarChartAt(
    run: () -> BarRun,
    elapsedMs: () -> Float,
    what: String,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val composed = run()
    BarChart(
        bars = composed.bars,
        what = what,
        valueLabel = valueLabel,
        modifier = modifier,
        barScale = { index -> run().scaleAt(index, elapsedMs()) },
        valueAlpha = { run().valuesAt(elapsedMs()) },
    )
}
