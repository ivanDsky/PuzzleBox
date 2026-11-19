package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.insights.domain.stats.HintFreeRateSeries
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import kotlin.math.roundToInt

/**
 * The hint-free rate section: the share of solves with no hint as a line on a 0 to 100% scale, per week (per day for
 * the Week range). With one point there is no line, so the share shows as a number.
 */
@Composable
internal fun HintFreeSection(state: SectionState<HintFreeRateSeries>, modifier: Modifier = Modifier) {
    StatsSectionFrame(title = "Hint-free rate", state = state, tag = "stats.hintFree", modifier = modifier) { series ->
        val withYear = series.points.map { it.start.year }.distinct().size > 1
        val what = "Hint-free solves per " + if (series.granularity == StatsGranularity.Day) "day" else "week"
        val latest = series.points.last()
        StatsTrendBlock(
            tag = "stats.hintFree",
            what = what,
            line = if (series.isLine) {
                series.points.map { LineChartPoint(it.start.toEpochDay().toDouble(), it.share, it.start.axisLabel(withYear)) }
            } else {
                null
            },
            valueLabel = ::percentLabel,
            valueRange = 0.0..1.0,
            singleValue = percentLabel(latest.share),
            singleDetail = latest.start.bucketLabel(series.granularity) + " · ${latest.hintFree} of " + plural(latest.solved, "solve"),
        )
    }
}

private fun percentLabel(share: Double): String = "${(share * 100).roundToInt()}%"
