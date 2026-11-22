package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.stats.grouped.StatsCountUpText
import com.ivandsky.puzzlebox.insights.stats.grouped.StatsLineChart
import com.ivandsky.puzzlebox.insights.stats.grouped.StatsMorphText

/**
 * What a trend section shows once it has a series: the line chart of [line] on a card, or, when the series has fewer
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
    StatsChartCard(modifier) {
        if (line != null) {
            StatsLineChart(line, what, valueLabel, key = "$tag.line", modifier = Modifier.testTag("$tag.line"), valueRange = valueRange)
        } else {
            Column(Modifier.semantics(mergeDescendants = true) {}.testTag("$tag.single")) {
                StatsCountUpText(
                    singleValue,
                    key = "$tag.single",
                    style = MaterialTheme.typography.headlineSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatsMorphText(
                    "$what · $singleDetail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
