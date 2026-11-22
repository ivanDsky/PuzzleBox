package com.ivandsky.puzzlebox.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * A flat line over time: one stroke through [points], faint rules at its lowest and highest value with those values
 * written beside them, and the first and last point's label under the ends. [what] says what the line measures, for the
 * screen reader's summary ([lineChartSummary]); [valueLabel] writes a value. A [valueRange] fixes the scale (a share
 * from 0 to 1) where the default fits the points' own lowest and highest. A line needs 2 points: with fewer nothing is
 * drawn, and the caller shows the value as a number instead. All equal values draw a flat line mid-way with its one value.
 * The time axis runs the way the layout reads, so it mirrors in a right-to-left layout.
 *
 * Two optional lambdas let a caller animate the chart without the chart owning a clock; both are read at draw time and
 * default to the finished chart. [drawProgress] (0..1) draws only that share of the line's length from its start, and
 * [labelAlpha] (0..1) is the opacity of the axis values and the end labels, and [dotAlpha] that of the dots (the same
 * unless given). A caller morphing between two datasets draws the labels of one while the shape is between both:
 * [labelPoints] are the points whose first and last label go under the ends and [labelRange] the scale whose lowest and
 * highest value are written at the rules (the drawn [points] and [valueRange] unless given). [summaryPoints] are what
 * the screen reader's summary describes where that is not the drawn [points] (a morphing line is drawn on a resampled
 * grid, but the summary must stay the data it will end on).
 */
@Composable
fun LineChart(
    points: List<LineChartPoint>,
    what: String,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Double>? = null,
    drawProgress: () -> Float = { 1f },
    labelAlpha: () -> Float = { 1f },
    dotAlpha: () -> Float = labelAlpha,
    labelPoints: List<LineChartPoint> = points,
    labelRange: ClosedFloatingPointRange<Double>? = null,
    summaryPoints: List<LineChartPoint> = points,
) {
    if (points.size < 2) return
    val ordered = remember(points) { points.sortedBy { it.x } }
    val scale = remember(ordered, valueRange) { valueRange ?: (ordered.minOf { it.y }..ordered.maxOf { it.y }) }
    val labelOrdered = remember(labelPoints) { labelPoints.sortedBy { it.x } }
    val labelScale = labelRange ?: scale
    val summary = remember(summaryPoints, what) { lineChartSummary(what, summaryPoints.sortedBy { it.x }, valueLabel) }
    val labelStyle = MaterialTheme.typography.labelSmall.tabular()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelFade = Modifier.graphicsLayer { alpha = labelAlpha() }
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.height(PlotHeight).then(labelFade), verticalArrangement = if (labelScale.start == labelScale.endInclusive) Arrangement.Center else Arrangement.SpaceBetween) {
                Text(valueLabel(labelScale.endInclusive), style = labelStyle, color = labelColor)
                if (labelScale.start != labelScale.endInclusive) Text(valueLabel(labelScale.start), style = labelStyle, color = labelColor)
            }
            Column(Modifier.weight(1f)) {
                Plot(ordered, scale, drawProgress, dotAlpha, Modifier.fillMaxWidth().height(PlotHeight))
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs).then(labelFade), verticalAlignment = Alignment.Top) {
                    Text(labelOrdered.first().label, style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
                    Text(labelOrdered.last().label, style = labelStyle, color = labelColor)
                }
            }
        }
    }
}

@Composable
private fun Plot(
    points: List<LineChartPoint>,
    scale: ClosedFloatingPointRange<Double>,
    drawProgress: () -> Float,
    dotAlpha: () -> Float,
    modifier: Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    val rule = MaterialTheme.colorScheme.outlineVariant
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxSize()) {
        val inset = PlotInset.toPx()
        val top = inset
        val bottom = size.height - inset
        val ruleWidth = RuleWidth.toPx()
        val flat = scale.start == scale.endInclusive
        if (flat) {
            drawRule((top + bottom) / 2, rule, ruleWidth)
        } else {
            drawRule(top, rule, ruleWidth)
            drawRule(bottom, rule, ruleWidth)
        }
        val xs = points.first().x..points.last().x
        val dotRoom = LatestDotRadius.toPx()
        val offsets = points.map { point ->
            val along = if (xs.start == xs.endInclusive) 0.5f else ((point.x - xs.start) / (xs.endInclusive - xs.start)).toFloat()
            val x = dotRoom + (size.width - 2 * dotRoom) * (if (mirrored) 1f - along else along)
            val up = if (flat) 0.5f else ((point.y - scale.start) / (scale.endInclusive - scale.start)).toFloat()
            Offset(x, bottom - (bottom - top) * up)
        }
        val path = Path().apply {
            offsets.forEachIndexed { index, offset -> if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y) }
        }
        val stroke = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val progress = drawProgress().coerceIn(0f, 1f)
        if (progress >= 1f) {
            drawPath(path, line, style = stroke)
        } else if (progress > 0f) {
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * progress, partial, true)
            drawPath(partial, line, style = stroke)
        }
        val dotsAlpha = dotAlpha().coerceIn(0f, 1f)
        // Few points get a dot each; a long line keeps only its latest point so the stroke stays clean.
        val dotted = if (points.size <= MaxDots) offsets else listOf(offsets.last())
        dotted.forEach { drawCircle(line, DotRadius.toPx(), it, alpha = dotsAlpha) }
        // The latest point is a larger dot, so it reads as the current value.
        drawCircle(line, LatestDotRadius.toPx(), offsets.last(), alpha = dotsAlpha)
    }
}

private fun DrawScope.drawRule(y: Float, color: Color, width: Float) =
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = width)

private val PlotHeight: Dp = 120.dp

// Half a label line, so a rule sits level with the centre of the value written beside it.
private val PlotInset: Dp = 8.dp
private val RuleWidth: Dp = 1.dp
private val LineWidth: Dp = 2.dp
private val DotRadius: Dp = 3.dp
private val LatestDotRadius: Dp = 5.dp
private const val MaxDots = 16
