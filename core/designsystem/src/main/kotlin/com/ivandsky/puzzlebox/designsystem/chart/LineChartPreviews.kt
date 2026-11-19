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
import com.ivandsky.puzzlebox.designsystem.formatDuration

// Previews: a time line, a share line on a fixed scale, a flat line and a long one. Internal so Showkase's index can reach them.

internal val PreviewWeeks = listOf("Oct 26", "Nov 2", "Nov 9", "Nov 16", "Nov 23", "Nov 30")

internal fun previewTimes(vararg seconds: Int): List<LineChartPoint> =
    seconds.mapIndexed { index, value -> LineChartPoint(index * 7.0, value * 1000.0, PreviewWeeks.getOrElse(index) { "Week ${index + 1}" }) }

internal fun previewShares(vararg percent: Int): List<LineChartPoint> =
    percent.mapIndexed { index, value -> LineChartPoint(index * 7.0, value / 100.0, PreviewWeeks.getOrElse(index) { "Week ${index + 1}" }) }

internal val PreviewTime: (Double) -> String = { formatDuration(it.toLong()) }
internal val PreviewShare: (Double) -> String = { "${(it * 100).toInt()}%" }

@Composable
private fun PreviewColumn(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) { content() }
    }
}

@ShowkaseComposable(name = "LineChart (time)", group = "Charts")
@PuzzleBoxPreviews
@Composable
internal fun LineChartTimePreview() {
    PreviewColumn { LineChart(previewTimes(95, 88, 102, 76, 71, 64), "Average solve time", PreviewTime) }
}

@ShowkaseComposable(name = "LineChart (share, fixed scale)", group = "Charts")
@PuzzleBoxPreviews
@Composable
internal fun LineChartShareAtScalePreview() {
    PreviewColumn { LineChart(previewShares(40, 55, 50, 80), "Hint-free solves", PreviewShare, valueRange = 0.0..1.0) }
}

@ShowkaseComposable(name = "LineChart (flat and two points)", group = "Charts")
@PuzzleBoxPreviews
@Composable
internal fun LineChartFlatPreview() {
    PreviewColumn {
        LineChart(previewTimes(45, 45, 45), "Average solve time", PreviewTime)
        LineChart(previewTimes(90, 60), "Average solve time", PreviewTime)
    }
}

@ShowkaseComposable(name = "LineChart (long)", group = "Charts")
@PuzzleBoxPreviews
@Composable
internal fun LineChartLongPreview() {
    val seconds = IntArray(40) { 80 + ((it * 7) % 23) - it / 3 }
    PreviewColumn { LineChart(previewTimes(*seconds), "Average solve time", PreviewTime) }
}
