package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.domain.stats.StatsBucket
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One bar of the river: a puzzle day of a month, or a Monday-to-Sunday week of a year, with its play time and solves. */
internal data class RiverBar(val start: LocalDate, val timeMs: Long, val solved: Int)

/**
 * The river's bars, oldest first. A month has one for every day of it, a day with no play a stub; a year has one for each
 * week with play (the first clipped to 1 January), so the same weeks as the stats trend lines.
 */
internal fun RecapCard.TotalTime.riverBars(): List<RiverBar> = when (val period = period) {
    is RecapPeriod.Month -> {
        val byDay = days.associateBy { it.day }
        (1..period.month.lengthOfMonth()).map { number ->
            val day = period.month.atDay(number)
            RiverBar(day, byDay[day]?.timeMs ?: 0L, byDay[day]?.solved ?: 0)
        }
    }
    is RecapPeriod.Year ->
        StatsBucket.of(days, StatsGranularity.Week, period.first).map { RiverBar(it.start, it.timeMs, it.solved) }
}

/** The labels under the river: the first, middle and last bar's day ("1 Oct", "16 Oct", "31 Oct") or, for weeks, month ("Jan", "Jun", "Dec"). */
internal fun RecapCard.TotalTime.axisLabels(bars: List<RiverBar>): List<String> {
    if (bars.isEmpty()) return emptyList()
    val pattern = if (period is RecapPeriod.Month) "d MMM" else "MMM"
    val format = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return listOf(bars.first(), bars[bars.size / 2], bars.last()).map { it.start.format(format) }
}

/** The line under the river: what it is ([label]), when ([title]) and how much ([detail]). */
internal data class RiverReadout(val label: String, val title: String, val detail: String)

/** The readout of bar [selected], or of the busiest bar (the first, on a tie) when nothing is held; null with no play at all. */
internal fun RecapCard.TotalTime.readout(bars: List<RiverBar>, selected: Int?): RiverReadout? {
    val busiest = bars.indices.maxByOrNull { bars[it].timeMs }?.takeIf { bars[it].timeMs > 0 } ?: return null
    val bar = bars[selected ?: busiest]
    val monthly = period is RecapPeriod.Month
    val unit = if (monthly) "day" else "week"
    val title = if (monthly) bar.start.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) else "Week of ${bar.start.shortLabel()}"
    val puzzles = if (bar.solved == 1) "1 puzzle" else "${bar.solved} puzzles"
    return RiverReadout(
        label = if (selected == null) "Busiest $unit" else unit.replaceFirstChar { it.uppercase() },
        title = title,
        detail = "${formatReadoutDuration(bar.timeMs)} · $puzzles",
    )
}

/**
 * Which bar the player's finger is on, shared by the story's river and the tablet fact's mini river so they scrub in
 * step. Provided once above both; a river outside any provider keeps its own.
 */
internal val LocalRiverScrub = compositionLocalOf<MutableState<Int?>?> { null }

@Composable
internal fun RiverScrubProvider(content: @Composable () -> Unit) {
    val scrub = remember { mutableStateOf<Int?>(null) }
    CompositionLocalProvider(LocalRiverScrub provides scrub, content = content)
}

@Composable
internal fun rememberRiverScrub(): MutableState<Int?> = LocalRiverScrub.current ?: remember { mutableStateOf(null) }

/**
 * The bars, drawn left to right. [wave] (0..1, read as it draws) is how far the arrival wave has run: each bar rises from
 * a stub to its height in [BarRiseMs] of a [WaveMs] wave. With [onScrub] a finger on the river reads the bar under it
 * and holds it highlighted until it lifts; the river takes the touch for itself, so the deck does not swipe while it is
 * on it. A river without [onScrub] only draws. A screen reader skips the bars: the readout and the hero say it.
 */
@Composable
internal fun TotalTimeRiver(
    bars: List<RiverBar>,
    selected: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    wave: () -> Float = { 1f },
    gap: Dp = 2.dp,
    onScrub: ((Int?) -> Unit)? = null,
) {
    val maxMs = bars.maxOfOrNull { it.timeMs } ?: 0L
    Canvas(
        modifier
            .clearAndSetSemantics { }
            .then(if (onScrub == null) Modifier else Modifier.pointerInput(bars.size) { scrubOver(bars.size, onScrub) }),
    ) {
        val rule = BaseLine.toPx()
        val floor = size.height - rule
        val stub = Stub.toPx()
        val step = gap.toPx()
        val width = ((size.width - step * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
        val elapsed = wave() * WaveMs
        bars.forEachIndexed { index, bar ->
            val start = (WaveMs - BarRiseMs) * index / (bars.size - 1).coerceAtLeast(1)
            val rise = MotionTokens.EasingStandard.transform(((elapsed - start) / BarRiseMs).coerceIn(0f, 1f))
            val full = if (maxMs == 0L) stub else stub + (floor - stub) * bar.timeMs / maxMs
            val height = stub + (full - stub) * rise
            drawRoundRect(
                color = color.copy(alpha = if (selected == index) 1f else BarAlpha),
                topLeft = Offset(index * (width + step), floor - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(width.coerceAtMost(MaxCorner.toPx()) / 2f),
            )
        }
        drawRect(color, Offset(0f, floor), Size(size.width, rule))
    }
}

/** Reads the bar under the finger from its first touch until it lifts, taking every move for the river; ends with no bar. */
private suspend fun PointerInputScope.scrubOver(count: Int, onScrub: (Int?) -> Unit) {
    fun barAt(x: Float) = (x / size.width * count).toInt().coerceIn(0, count - 1)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onScrub(barAt(down.position.x))
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                change.consume()
                onScrub(barAt(change.position.x))
            }
        } finally {
            onScrub(null)
        }
    }
}

/** The wave runs this long (Sweep, the river's Linear run) and each bar takes [BarRiseMs] of it to reach its height. */
internal val WaveMs = RecapMotion.SweepRunMax.toFloat()
private const val BarRiseMs = 360f

/** Bars the ink at 78% (over the 70% floor for a meaningful graphic); the held bar is the full ink. */
private const val BarAlpha = 0.78f
private val Stub = 3.dp
private val BaseLine = 2.dp
private val MaxCorner = 4.dp
