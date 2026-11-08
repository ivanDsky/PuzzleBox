package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular

internal const val YOU_LABEL = "You"

private val ChartHeight = 132.dp
private val YouLabelRoom = 22.dp
private val BarGap = 6.dp
private val BarMinHeight = 6.dp
private val BarTopRadius = 6.dp
private val BarBottomRadius = 2.dp
private val GhostStroke = 1.5.dp
private const val DIM_ALPHA = 0.4f
private const val GROW_FROM = 0.05f
private const val GROW_STAGGER_MS = 20
private const val GROW_STAGGER_CAP = 10

/** The first-solve placeholder: ten dashed bars of a made-up spread; the fifth, in full, is this solve. */
private val GhostHeights = listOf(0.27f, 0.45f, 0.72f, 0.54f, 0.81f, 0.45f, 0.27f, 0.18f, 0.09f, 0.36f)
private const val GHOST_YOU_BIN = 4
private const val GHOST_YOU_HEIGHT = 0.25f

/**
 * The time spread chart (spec: Results hero, side card): ten bins of the player's solves, bars
 * in [accent] at 40 percent with this solve's bin in full under a "You" label; empty bins are
 * not drawn. A first solve ([HeroSpread.bins] empty) draws dashed ghost bars and the
 * first-time line. The axis and the caption sit underneath. Bars grow from 5 percent with a
 * 20 ms stagger capped at ten bars, from the blocks beat; reduced motion shows them at once.
 * The bars read aloud as one [HeroSpread.description]; the caption is read as itself.
 */
@Composable
internal fun TimeSpreadChart(spread: HeroSpread, accent: Color, modifier: Modifier = Modifier) {
    val ghost = spread.bins.isEmpty()
    Column(modifier) {
        Column(Modifier.testTag("result.side.chart").clearAndSetSemantics { contentDescription = spread.description }) {
            Row(
                modifier = Modifier.fillMaxWidth().height(ChartHeight).padding(top = YouLabelRoom),
                horizontalArrangement = Arrangement.spacedBy(BarGap),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (ghost) {
                    GhostHeights.forEachIndexed { index, height ->
                        val you = index == GHOST_YOU_BIN
                        Slot(index, if (you) GHOST_YOU_HEIGHT else height, you, ghost = !you, accent, Modifier.weight(1f))
                    }
                } else {
                    spread.bins.forEachIndexed { index, bin ->
                        if (bin.count == 0) {
                            Box(Modifier.weight(1f))
                        } else {
                            Slot(index, bin.height, index == spread.youBin, ghost = false, accent, Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(spread.axisLow, style = AxisStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("result.side.axis.low"))
                Text(spread.axisHigh, style = AxisStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("result.side.axis.high"))
            }
        }
        val line = spread.firstTimeLine
        if (line != null) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm).testTag("result.side.first"),
            )
        }
        val caption = spread.caption
        if (caption != null) {
            Text(
                text = caption.annotated(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm).testTag("result.side.caption"),
            )
        }
    }
}

@Composable
private fun AxisStyle() = MaterialTheme.typography.labelMedium.tabular()

/** One bin's column: the bar of [fraction] of the plot, the "You" label above it when [you], dashed when [ghost]. */
@Composable
private fun Slot(index: Int, fraction: Float, you: Boolean, ghost: Boolean, accent: Color, modifier: Modifier) {
    val grow = rememberGrow(index)
    val outline = MaterialTheme.colorScheme.outline
    val plot = ChartHeight - YouLabelRoom
    val barHeight: Dp = maxOf(BarMinHeight, plot * fraction.coerceIn(0f, 1f))
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
        if (you) {
            Text(
                text = YOU_LABEL,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                color = accent,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer { alpha = grow.value }
                    .testTag("result.side.you"),
            )
        }
        val bar = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .graphicsLayer {
                scaleY = grow.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
        if (ghost) {
            Box(bar.drawBehind { drawGhostOutline(outline, GhostStroke.toPx(), BarTopRadius.toPx()) }.testTag("result.side.bar.ghost.$index"))
        } else {
            val shape = RoundedCornerShape(BarTopRadius, BarTopRadius, BarBottomRadius, BarBottomRadius)
            Box(bar.background(if (you) accent else accent.copy(alpha = DIM_ALPHA), shape).testTag(if (you) "result.side.bar.you" else "result.side.bar.$index"))
        }
    }
}

/** The grow progress of bar [index]: 5 percent to 1 after the blocks beat, a stagger of 20 ms per bar up to ten; settled in a preview and under reduced motion. */
@Composable
private fun rememberGrow(index: Int): Animatable<Float, *> {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val grow = remember { Animatable(if (settled || reduced) 1f else GROW_FROM) }
    LaunchedEffect(grow, reduced) {
        if (reduced) {
            grow.snapTo(1f)
        } else {
            val stagger = index.coerceIn(0, GROW_STAGGER_CAP - 1) * GROW_STAGGER_MS
            grow.animateTo(
                1f,
                tween(
                    durationMillis = MotionTokens.EnterDuration,
                    delayMillis = beatDelayMs(ResultBeats.Blocks) + stagger,
                    easing = MotionTokens.EasingEmphasizedDecelerate,
                ),
            )
        }
    }
    return grow
}

/** A dashed outline of a bar open at the bottom: up the left edge, over the rounded top, down the right edge. */
private fun DrawScope.drawGhostOutline(color: Color, strokeWidth: Float, radius: Float) {
    val r = radius.coerceAtMost(size.width / 2f)
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        lineTo(size.width - r, 0f)
        arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), 270f, 90f, false)
        lineTo(size.width, size.height)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 3, strokeWidth * 2))))
}

/** The caption with its [Emphasized.bold] stretch in bold. */
private fun Emphasized.annotated(): AnnotatedString = buildAnnotatedString {
    val range = bold
    if (range == null) {
        append(text)
    } else {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(range.first, range.last + 1)) }
        append(text.substring(range.last + 1))
    }
}
