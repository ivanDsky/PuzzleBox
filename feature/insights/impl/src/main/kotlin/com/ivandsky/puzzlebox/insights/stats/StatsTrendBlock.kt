package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.chart.LineChart
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * What a trend section shows once it has a series: the [LineChart] of [line] on a card, or, when the series has fewer
 * than 2 points and [line] is null, its one value as a number ([singleValue] over its [singleDetail]); a line needs 2
 * points. [what] names what the line measures, [valueLabel] writes a value, [valueRange] fixes the scale. [tag] names
 * the block for tests: `.line` or `.single`.
 */
@Composable
internal fun StatsTrendBlock(
    tag: String,
    what: String,
    line: List<LineChartPoint>?,
    valueLabel: (Double) -> String,
    singleValue: String,
    singleDetail: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Double>? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Spacing.lg),
    ) {
        if (line != null) {
            LineChart(line, what, valueLabel, Modifier.testTag("$tag.line"), valueRange)
        } else {
            Column(Modifier.semantics(mergeDescendants = true) {}.testTag("$tag.single")) {
                Text(singleValue, style = MaterialTheme.typography.headlineSmall.tabular(), color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "$what · $singleDetail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
