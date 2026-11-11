package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular

private val LabelRoom = 22.dp
private val LabelGap = 8.dp

/**
 * The labels under the bins: each bin's time range when the widest fits its slot, else the bin
 * edges at every k-th place, k the fewest that keep neighbours a [LabelGap] apart; an edge label
 * is centred on its edge but kept inside the chart. They fade in as a block.
 */
@Composable
internal fun RangeLabels(view: HeroScopeView, entrance: Boolean, width: Dp) {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val alpha = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (settled) return@LaunchedEffect
        alpha.animateTo(
            1f,
            if (reduced) {
                tween(MotionTokens.ReducedFadeDuration)
            } else {
                tween(MotionTokens.ChartLabelFade, delayMillis = if (entrance) beatDelayMs(ResultBeats.Blocks) + MotionTokens.EnterDuration else 0)
            },
        )
    }
    val measurer = rememberTextMeasurer()
    val style: TextStyle = MaterialTheme.typography.labelSmall.tabular()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val totalPx = with(density) { width.toPx() }
    val gapPx = with(density) { LabelGap.toPx() }
    val bins = view.bars.size
    val slotPx = totalPx / bins
    val ranges = view.bars.map { measurer.measure(it.range, style, maxLines = 1, softWrap = false).size.width }
    val showRanges = ranges.max() + gapPx <= slotPx
    Box(Modifier.fillMaxWidth().height(LabelRoom).padding(top = 6.dp).graphicsLayer { this.alpha = alpha.value }) {
        if (showRanges) {
            view.bars.forEachIndexed { index, bar ->
                val x = (slotPx * index + (slotPx - ranges[index]) / 2f).coerceIn(0f, (totalPx - ranges[index]).coerceAtLeast(0f))
                Label(bar.range, style, color, x, Modifier.testTag("result.side.range.$index"))
            }
        } else {
            val widths = view.edges.map { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width.toFloat() }
            placeEdgeLabels(widths, slotPx, totalPx, gapPx).forEach { (index, x) ->
                Label(view.edges[index], style, color, x, Modifier.testTag("result.side.edge.$index"))
            }
        }
    }
}

/** An edge label's index and its left x in px. */
internal data class PlacedLabel(val index: Int, val x: Float)

/**
 * Where the edge labels go: label `i` is centred on its edge, `i * slotPx`. Labels thin to every
 * k-th edge, k the fewest that keep neighbours [gapPx] apart; one that would leave `0..totalPx`
 * or touch the previous drawn label within [gapPx] is skipped, never shifted.
 */
internal fun placeEdgeLabels(widths: List<Float>, slotPx: Float, totalPx: Float, gapPx: Float): List<PlacedLabel> {
    val widest = widths.max()
    var every = 1
    while (every < widths.size && slotPx * every < widest + gapPx) every++
    val placed = mutableListOf<PlacedLabel>()
    var previousEnd = Float.NEGATIVE_INFINITY
    widths.forEachIndexed { index, width ->
        if (index % every != 0) return@forEachIndexed
        val x = slotPx * index - width / 2f
        if (x < 0f || x + width > totalPx || x < previousEnd + gapPx) return@forEachIndexed
        placed += PlacedLabel(index, x)
        previousEnd = x + width
    }
    return placed
}

@Composable
private fun Label(text: String, style: TextStyle, color: Color, x: Float, modifier: Modifier) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.offset { IntOffset(x.toInt(), 0) },
    )
}
