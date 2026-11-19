package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries

/**
 * The time trend section: the average solve time as a line, per week (per day for the Week range). With one point
 * there is no line, so the average shows as a number.
 */
@Composable
internal fun TrendSection(state: SectionState<TimeTrendSeries>, modifier: Modifier = Modifier) {
    StatsSectionFrame(title = "Time trend", state = state, tag = "stats.trend", modifier = modifier) { series ->
        val withYear = series.points.map { it.start.year }.distinct().size > 1
        val what = "Average solve time per " + if (series.granularity == StatsGranularity.Day) "day" else "week"
        val latest = series.points.last()
        StatsTrendBlock(
            tag = "stats.trend",
            what = what,
            line = if (series.isLine) {
                series.points.map { LineChartPoint(it.start.toEpochDay().toDouble(), it.averageMs.toDouble(), it.start.axisLabel(withYear)) }
            } else {
                null
            },
            valueLabel = { formatDuration(it.toLong()) },
            singleValue = formatDuration(latest.averageMs),
            singleDetail = latest.start.bucketLabel(series.granularity) + " · " + plural(latest.solved, "solve"),
        )
    }
}
