package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalIce
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.RollingDigits
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One tick of the ring: a day, or a week in a year. [fill] is the share of it inside the streak, 0..1. */
internal class DialTick(val fill: Float, val frozen: Boolean, val future: Boolean)

/** The ring's [ticks] and where the streak lights them: [count] ticks from index [first]. */
internal class DialRing(val ticks: List<DialTick>, val first: Int, val count: Int, val weekly: Boolean)

/**
 * The ring for [card]: a tick per day of its span, or per Monday-start calendar week from Jan 1 for a year (53 in
 * 2026), a week filled by the share of its days in the year that the streak ran. A tick whose first day in the span
 * is after the card's puzzle day [RecapCard.Streak.today] is still to come.
 */
internal fun dialRing(card: RecapCard.Streak): DialRing {
    val span = card.span
    val weekly = span is RecapPeriod.Year
    val days = generateSequence(span.first) { it.plusDays(1).takeIf { next -> next in span } }.toList()
    val weekZero = span.first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val groups = if (weekly) days.groupBy { ChronoUnit.WEEKS.between(weekZero, it) }.values.toList() else days.map { listOf(it) }
    val ticks = groups.map { group ->
        DialTick(
            fill = group.count { it in card.from..card.to }.toFloat() / group.size,
            frozen = group.any { it in card.frozenDays },
            future = card.today?.let { group.first().isAfter(it) } == true,
        )
    }
    val first = ticks.indexOfFirst { it.fill > 0f }
    val last = ticks.indexOfLast { it.fill > 0f }
    return DialRing(ticks, first.coerceAtLeast(0), if (first < 0) 0 else last - first + 1, weekly)
}

/** How long the comet takes round the streak: min(SweepRunMax, SweepRunBase + SweepRunPerDay × days) ms. */
internal fun sweepMs(days: Int): Int = min(RecapMotion.SweepRunMax, RecapMotion.SweepRunBase + RecapMotion.SweepRunPerDay * days)

/**
 * The comet dial: a ring of ticks with the longest streak lit in order by one clock that also drives the comet and the
 * odometer in the centre, so they cannot drift. Ice ticks crack a beat after the comet passes; [onSettled] is called
 * once the sweep and its pulse are over (at once under reduced motion and in a preview, where the final ring shows).
 */
@Composable
internal fun StreakDial(card: RecapCard.Streak, modifier: Modifier = Modifier, onSettled: () -> Unit = {}) {
    val settled = entranceSettled()
    val reduced = rememberReducedMotion()
    val ring = remember(card) { dialRing(card) }
    val total = sweepMs(card.days)
    val sweep = remember { Animatable(if (settled || reduced) 1f else 0f) }
    val pulse = remember { Animatable(0f) }
    val reveal = remember { Animatable(if (reduced && !settled) 0f else 1f) }
    LaunchedEffect(card) {
        when {
            settled -> Unit
            reduced -> reveal.animateTo(1f, tween(MotionTokens.ReducedFadeDuration))
            else -> {
                sweep.animateTo(1f, tween(total, delayMillis = RecapMotion.CountHeroDelay, easing = RecapMotion.EasingSweep))
                pulse.animateTo(1f, tween(MotionTokens.PulseDuration))
            }
        }
        onSettled()
    }
    val ink = PaletteEntry.Streak.ink
    // Light: the pale container reads on the gold. Dark: the reference tick sits a step under the pale onContainer
    // (the chip's label ink), so it is the ice pair blended toward onContainer rather than that ink itself.
    val ice = LocalIce.current.let {
        if (it.container.luminance() > it.onContainer.luminance()) it.container else lerp(it.container, it.onContainer, DARK_ICE_MIX)
    }
    Box(
        modifier
            .graphicsLayer {
                alpha = reveal.value
                val scale = 1f + (MotionTokens.PulseScale - 1f) * sin(PI.toFloat() * pulse.value)
                scaleX = scale
                scaleY = scale
            }
            .testTag("recap.streak.dial"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) { drawRing(ring, sweep.value, total, !reduced, ink, ice) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // The roll runs on the sweep's clock; a screen reader gets the settled length, never a digit mid-roll.
            Box(
                Modifier
                    .semantics { contentDescription = "Longest streak, ${card.days.daysInARow()}"; text = AnnotatedString(card.days.toString()) }
                    .testTag("recap.streak.days"),
            ) {
                RollingDigits(
                    value = card.days.toLong(),
                    style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, lineHeight = HeroFontSize, fontWeight = FontWeight.ExtraBold),
                    color = ink,
                    delayMs = RecapMotion.CountHeroDelay,
                    durationMs = total,
                    staggerMs = 0,
                )
            }
            Text(
                text = if (card.days == 1) "day in a row" else "days in a row",
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = ink,
            )
        }
    }
}

private fun DrawScope.drawRing(ring: DialRing, sweep: Float, totalMs: Int, comet: Boolean, ink: Color, ice: Color) {
    val n = ring.ticks.size
    if (n == 0) return
    val r = size.minDimension / 2f
    val outer = r * (if (ring.weekly) WeekOuter else DayOuter)
    val length = r * (if (ring.weekly) WeekLength else DayLength)
    val width = r * (if (ring.weekly) WeekWidth else DayWidth)
    val center = Offset(size.width / 2f, size.height / 2f)
    // Time in ticks: how many ticks the comet covers in [ms] at its average speed.
    val perMs = ring.count.toFloat() / totalMs
    val igniteTicks = max(MIN_TICKS, TICK_IGNITE_MS * perMs)
    val crackTicks = max(MIN_TICKS, TICK_CRACK_MS * perMs)
    val head = sweep * (ring.count + igniteTicks + crackTicks)
    fun unit(index: Float): Offset {
        val angle = (-PI / 2 + 2 * PI * index / n).toFloat()
        return Offset(cos(angle), sin(angle))
    }
    ring.ticks.forEachIndexed { i, tick ->
        val u = unit(i.toFloat())
        val from = center + u * (outer - length)
        val to = center + u * outer
        val ignite = if (tick.fill > 0f) ((head - (i - ring.first)) / igniteTicks).coerceIn(0f, 1f) else 0f
        val burning = ignite > 0f
        if (!(tick.frozen && burning)) {
            drawLine(ink.copy(alpha = if (tick.future) FUTURE_ALPHA else OFF_ALPHA), from, to, width, StrokeCap.Round)
        }
        if (!burning) return@forEachIndexed
        val eased = MotionTokens.Trace.EasingOut.transform(ignite)
        val grown = length * (IGNITE_FROM + (1f - IGNITE_FROM) * eased) * tick.fill
        if (!tick.frozen) {
            drawLine(ink.copy(alpha = eased), from, from + u * grown, width, StrokeCap.Round)
        } else if (ring.weekly) {
            drawCircle(ice, width * 0.6f, to + u * (width * 3f))
        } else {
            val crack = ((head - (i - ring.first) - igniteTicks) / crackTicks).coerceIn(0f, 1f)
            val side = Offset(-u.y, u.x) * (CRACK_DP.dp.toPx() * crack)
            val half = length * HALF
            drawCircle(ink.copy(alpha = CONNECTOR_ALPHA * crack), width / 2f, from + u * (length / 2f))
            drawLine(ice, from - side, from + u * half - side, width, StrokeCap.Round)
            drawLine(ice, to - u * half + side, to + side, width, StrokeCap.Round)
        }
    }
    val cometIndex = ring.first + min(head, ring.count - 1f)
    val cometAlpha = (1f - (head - (ring.count - 1f)).coerceIn(0f, 1f)) * if (sweep > 0f) 1f else 0f
    if (comet && cometAlpha > 0f) {
        repeat(TRAIL) { k ->
            val fade = cometAlpha * (1f - k.toFloat() / TRAIL)
            val u = unit(cometIndex - k * TRAIL_STEP)
            drawCircle(ink.copy(alpha = fade), width * (1.2f - 0.25f * k), center + u * (outer - length / 2f))
        }
    }
}

private fun Int.daysInARow(): String = if (this == 1) "1 day in a row" else "$this days in a row"

private val HeroFontSize = 104.sp
private const val DayOuter = 0.98f
private const val DayLength = 0.24f
private const val DayWidth = 0.06f
private const val WeekOuter = 0.93f
private const val WeekLength = 0.15f
private const val WeekWidth = 0.04f
private const val OFF_ALPHA = 0.3f
private const val CONNECTOR_ALPHA = 0.5f
private const val FUTURE_ALPHA = 0.15f
private const val IGNITE_FROM = 0.4f
private const val TICK_IGNITE_MS = 160f
private const val TICK_CRACK_MS = 140f
private const val MIN_TICKS = 0.5f
private const val CRACK_DP = 2
private const val HALF = 0.34f
private const val TRAIL = 3
private const val TRAIL_STEP = 0.7f
private const val DARK_ICE_MIX = 0.8f
