package com.ivandsky.puzzlebox.designsystem.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * A flat bar chart: one bar per entry of [bars], in the order given, each with its value written above it and its label
 * under it, standing on a faint baseline at 0. The tallest bar fills the plot and the others scale to it, so bar heights
 * compare directly. [what] says what the bars measure, for the screen reader's summary ([barChartSummary]); [valueLabel]
 * writes a value. One bar is a chart too, and no bars draw nothing. The bars run the way the layout reads, so they
 * mirror in a right-to-left layout.
 *
 * Two optional lambdas let a caller animate the chart without the chart owning a clock; both are read at draw time and
 * default to the finished chart. [barScale] gives the height scale of bar `index` (1 is its own height, 0 flat; it
 * grows from the baseline), and [valueAlpha] (0..1) is the opacity of the values written above the bars.
 */
@Composable
fun BarChart(
    bars: List<BarChartBar>,
    what: String,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    barScale: (Int) -> Float = { 1f },
    valueAlpha: () -> Float = { 1f },
) {
    if (bars.isEmpty()) return
    val summary = remember(bars, what) { barChartSummary(what, bars, valueLabel) }
    val tallest = remember(bars) { bars.maxOf { it.value } }
    val labelStyle = MaterialTheme.typography.labelSmall.tabular()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth().height(PlotHeight), horizontalArrangement = Arrangement.spacedBy(BarGap)) {
            bars.forEachIndexed { index, bar ->
                val fraction = if (tallest <= 0.0) 0f else (bar.value.coerceAtLeast(0.0) / tallest).toFloat()
                Bar(valueLabel(bar.value), fraction, { barScale(index) }, valueAlpha, Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(RuleWidth).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(BarGap)) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    style = labelStyle,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One bar with its [value] text on top: the text keeps its own height and the rest of the plot is shared by the gap above the bar and the bar, in the [fraction] of the tallest. */
@Composable
private fun Bar(value: String, fraction: Float, scale: () -> Float, valueAlpha: () -> Float, modifier: Modifier) {
    Column(modifier.height(PlotHeight), horizontalAlignment = Alignment.CenterHorizontally) {
        if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = valueAlpha() },
        )
        if (fraction > 0f) {
            val top = MaterialTheme.shapes.extraSmall.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
            Box(
                Modifier
                    .fillMaxWidth(BarWidth)
                    .weight(fraction)
                    .graphicsLayer {
                        scaleY = scale()
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(MaterialTheme.colorScheme.primary, top),
            )
        }
    }
}

private val PlotHeight: Dp = 120.dp
private val BarGap: Dp = Spacing.xs
private val RuleWidth: Dp = 1.dp
private const val BarWidth = 0.6f
