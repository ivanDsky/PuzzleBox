package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.chart.BarChart
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.WeekdayPattern
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The weekday pattern section: the average solve time of each weekday as a [BarChart], Monday first. A weekday with no
 * solve has no bar. The line under the chart names the quickest and slowest weekday when they differ.
 */
@Composable
internal fun WeekdaySection(state: SectionState<WeekdayPattern>, modifier: Modifier = Modifier) {
    StatsSectionFrame(title = "Weekday pattern", state = state, tag = "stats.weekday", modifier = modifier) { pattern ->
        val bars = pattern.days.map { BarChartBar(it.weekday.getDisplayName(TextStyle.SHORT, Locale.getDefault()), it.averageMs.toDouble()) }
        StatsChartCard {
            BarChart(bars, "Average solve time by weekday", { formatDuration(it.toLong()) }, Modifier.testTag("stats.weekday.chart"))
            val quickest = pattern.days.minBy { it.averageMs }
            val slowest = pattern.days.maxBy { it.averageMs }
            if (quickest.averageMs != slowest.averageMs) {
                Text(
                    "Quickest on ${quickest.weekday.fullName()}, slowest on ${slowest.weekday.fullName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md).testTag("stats.weekday.note"),
                )
            }
        }
    }
}

private fun DayOfWeek.fullName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())
