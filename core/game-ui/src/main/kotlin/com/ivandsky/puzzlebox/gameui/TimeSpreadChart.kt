package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val YOU_LABEL = "You"
internal const val PLAY_AGAIN_LABEL = "Replay"

private val PlotHeight = 110.dp
private val CountRoom = 16.dp
private val PillRoom = 22.dp
private val ChartHeight = PlotHeight + CountRoom + PillRoom
private val BarGap = 6.dp
private val BarMinHeight = 6.dp
private val BarTopRadius = 6.dp
private val BarBottomRadius = 2.dp
private val DashStroke = 1.5.dp
private const val DIM_ALPHA = 0.4f
private const val GROW_FROM = 0.05f
private const val GROW_STAGGER_MS = 20
private const val GROW_STAGGER_CAP = 10

/** The widest a bar draws: wide for a few bins, narrower as they multiply; the slots share the width. */
private fun maxBarWidth(bins: Int): Dp = when {
    bins <= 3 -> 96.dp
    bins <= 5 -> 72.dp
    else -> 64.dp
}

/** A bar's resting place in dp: centre across the chart, width and height. */
private class BarGeo(val cx: Float, val width: Float, val height: Float)

/** Where a bar starts a morph, relative to its resting place: a shift in dp and a scale per axis. */
private class BarStart(val dx: Float, val sx: Float, val sy: Float)

/** The geometry of [view]'s bars over [total] width; null for an empty bin, which draws nothing. */
private fun geometry(view: HeroScopeView, total: Dp): List<BarGeo?> {
    val slot = total.value / view.bars.size
    val width = minOf(maxBarWidth(view.bars.size).value, slot - BarGap.value).coerceAtLeast(2f)
    return view.bars.mapIndexed { index, bar ->
        if (bar.count == 0) {
            null
        } else {
            val height = maxOf(BarMinHeight.value, PlotHeight.value * bar.height.coerceIn(0f, 1f))
            BarGeo(slot * (index + 0.5f), width, height)
        }
    }
}

/** Each new bar starts at the nearest old bar not yet used; one with no partner scales in from [GROW_FROM]. */
private fun morphStarts(old: List<BarGeo?>, new: List<BarGeo?>): List<BarStart?> {
    val free = old.filterNotNull().toMutableList()
    return new.map { geo ->
        if (geo == null) return@map null
        val partner = free.minByOrNull { abs(it.cx - geo.cx) }
        if (partner == null) {
            BarStart(0f, 1f, GROW_FROM)
        } else {
            free.remove(partner)
            BarStart(partner.cx - geo.cx, partner.width / geo.width, partner.height / geo.height)
        }
    }
}

/** The geometry the chart last drew, so the next scope's bars can start from it. */
private class ChartMemory {
    var last: List<BarGeo?>? = null
}

/**
 * The time spread chart (spec: Results hero, side card): one slot per time range of the player's
 * solves, a count over each bar and this solve's bar in full under a "You" pill; the other bars
 * are [palette]'s accent at 40 percent and empty ranges draw nothing. A [playAgain] draws its bar as
 * a dashed outline under a "Replay" pill. Under the bars sit the time ranges when each fits its
 * slot, else the bin edges at every k-th place so they do not collide.
 *
 * Entrance: the bars grow from the blocks beat on a 20 ms stagger capped at ten bars, the counts
 * fade in after them and the pill lands last. When [view] changes the bars morph from the nearest
 * old bar; the counts and labels fade. Reduced motion is one short fade. The chart reads aloud as
 * one [HeroScopeView.description].
 */
@Composable
internal fun TimeSpreadChart(view: HeroScopeView, playAgain: Boolean, palette: GameAccent, modifier: Modifier = Modifier) {
    val memory = remember { ChartMemory() }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .testTag("result.side.chart")
            .clearAndSetSemantics { contentDescription = view.description },
    ) {
        val total = maxWidth
        val geo = geometry(view, total)
        val starts = remember(view) {
            val previous = memory.last
            memory.last = geo
            previous?.let { morphStarts(it, geo) }
        }
        val entrance = starts == null
        val slot = maxWidth / view.bars.size
        key(view) {
            Column {
                Box(Modifier.fillMaxWidth().height(ChartHeight)) {
                    view.bars.forEachIndexed { index, bar ->
                        val bars = geo[index] ?: return@forEachIndexed
                        BarColumn(
                            index = index,
                            bar = bar,
                            geo = bars,
                            start = starts?.get(index),
                            entrance = entrance,
                            you = index == view.youBin,
                            playAgain = playAgain,
                            palette = palette,
                            slot = slot,
                        )
                    }
                }
                RangeLabels(view, entrance, total)
            }
        }
    }
}

/** One bin's column: the pill when [you], the count and the bar, bottom-aligned in its [slot]. */
@Composable
private fun BarColumn(
    index: Int,
    bar: HeroBar,
    geo: BarGeo,
    start: BarStart?,
    entrance: Boolean,
    you: Boolean,
    playAgain: Boolean,
    palette: GameAccent,
    slot: Dp,
) {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val from = start ?: BarStart(0f, 1f, GROW_FROM)
    val progress = remember { Animatable(if (settled || reduced) 1f else 0f) }
    val barAlpha = remember { Animatable(if (settled || !reduced) 1f else 0f) }
    val countAlpha = remember { Animatable(if (settled) 1f else 0f) }
    val pillAlpha = remember { Animatable(if (settled) 1f else 0f) }
    val stamp = remember { Animatable(if (settled || reduced) 1f else MotionTokens.StampFromScale) }
    LaunchedEffect(Unit) {
        if (settled) return@LaunchedEffect
        if (reduced) {
            val fade = tween<Float>(MotionTokens.ReducedFadeDuration)
            launch { barAlpha.animateTo(1f, fade) }
            launch { countAlpha.animateTo(1f, fade) }
            launch { pillAlpha.animateTo(1f, fade) }
            return@LaunchedEffect
        }
        val barDelay = if (entrance) beatDelayMs(ResultBeats.Blocks) + index.coerceIn(0, GROW_STAGGER_CAP - 1) * GROW_STAGGER_MS else 0
        val barDuration = if (entrance) MotionTokens.EnterDuration else MotionTokens.ChartMorphDuration
        val barEasing = if (entrance) MotionTokens.EasingEmphasizedDecelerate else MotionTokens.EasingStandard
        val barEnd = barDelay + barDuration
        val countDelay = if (entrance) barEnd else barEnd - MotionTokens.ChartLabelFade
        val pillDelay = if (entrance) {
            beatDelayMs(ResultBeats.Blocks) + (GROW_STAGGER_CAP - 1) * GROW_STAGGER_MS + MotionTokens.EnterDuration
        } else {
            MotionTokens.ChartMorphDuration
        }
        launch { progress.animateTo(1f, tween(barDuration, barDelay, barEasing)) }
        launch { countAlpha.animateTo(1f, tween(MotionTokens.ChartLabelFade, countDelay)) }
        launch { pillAlpha.animateTo(1f, tween(MotionTokens.StampFadeDuration, pillDelay)) }
        launch {
            delay(pillDelay.toLong())
            stamp.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
        }
    }
    val density = LocalDensity.current
    val shift = with(density) { from.dx.dp.toPx() }
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(BarTopRadius, BarTopRadius, BarBottomRadius, BarBottomRadius)
    val look = when {
        you && playAgain -> Modifier.background(surface, shape).drawBehind { drawDashedBarOutline(outline, DashStroke.toPx(), BarTopRadius.toPx()) }
        you -> Modifier.background(palette.accent, shape)
        else -> Modifier.background(palette.accent.copy(alpha = DIM_ALPHA), shape)
    }
    Column(
        modifier = Modifier
            .offset(x = (geo.cx - slot.value / 2f).dp)
            .width(slot)
            .height(ChartHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (you) {
            Pill(
                text = if (playAgain) PLAY_AGAIN_LABEL else YOU_LABEL,
                playAgain = playAgain,
                palette = palette,
                modifier = Modifier.graphicsLayer {
                    alpha = pillAlpha.value
                    scaleX = stamp.value
                    scaleY = stamp.value
                },
            )
        }
        Text(
            text = bar.count.toString(),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .height(CountRoom)
                .graphicsLayer { alpha = countAlpha.value }
                .testTag("result.side.count.$index"),
        )
        Box(
            Modifier
                .width(geo.width.dp)
                .height(geo.height.dp)
                .graphicsLayer {
                    val p = progress.value
                    translationX = (1f - p) * shift
                    scaleX = lerp(from.sx, 1f, p)
                    scaleY = lerp(from.sy, 1f, p)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    alpha = barAlpha.value
                }
                .then(look)
                .testTag(if (you) "result.side.bar.you" else "result.side.bar.$index"),
        )
    }
}
