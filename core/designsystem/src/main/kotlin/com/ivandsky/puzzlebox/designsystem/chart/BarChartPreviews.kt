package com.ivandsky.puzzlebox.designsystem.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// Previews: a week of times, a few bars, one bar and equal bars. Internal so Showkase's index can reach them.

internal val PreviewWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Bars named by the weekday abbreviations, [seconds] high each, in order from Monday. */
internal fun previewWeekdayTimes(vararg seconds: Int): List<BarChartBar> =
    seconds.mapIndexed { index, value -> BarChartBar(PreviewWeekdays[index], value * 1000.0) }

@Composable
private fun PreviewColumn(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) { content() }
    }
}

@ShowkaseComposable(name = "BarChart (a week)", group = "Charts")
@PuzzleBoxPreviews
@Composable
internal fun BarChartWeekPreview() {
    PreviewColumn { BarChart(previewWeekdayTimes(95, 88, 102, 76, 71, 64, 58), "Average solve time by weekday", PreviewTime) }
}

@ShowkaseComposable(name = "BarChart (few, one and equal)", group = "Charts")
@PuzzleBoxPreviews
@Composable
internal fun BarChartFewPreview() {
    PreviewColumn {
        BarChart(previewWeekdayTimes(95, 60, 120), "Average solve time by weekday", PreviewTime)
        BarChart(previewWeekdayTimes(95), "Average solve time by weekday", PreviewTime)
        BarChart(previewWeekdayTimes(45, 45, 45, 45), "Average solve time by weekday", PreviewTime)
    }
}
