package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse

// The digit window is this many ems tall, so a rolling digit never clips its neighbour's glyph.
private const val WINDOW_EM = 1.1f
private const val DIGITS_PER_LAP = 10

/** [value]'s decimal digits, most significant first; a negative value reads as 0. */
internal fun digitsOf(value: Long): List<Int> = value.coerceAtLeast(0L).toString().map { it - '0' }

/** Extra laps for the column at [place] (0 = ones): the table's entry, else [beyond]. */
internal fun lapsAt(place: Int, table: List<Int>, beyond: Int): Int = table.getOrElse(place) { beyond }

/** Where a column's strip stands, in digit steps from 0, at [progress] (0..1): it ends [laps] laps past [digit]. */
internal fun odometerPosition(digit: Int, laps: Int, progress: Float): Float =
    (laps * DIGITS_PER_LAP + digit) * progress

/** The digit at the top of the window for a strip [position], and how far (0..1) it has scrolled out. */
internal fun windowAt(position: Float): Pair<Int, Float> {
    val whole = position.toInt()
    return (whole % DIGITS_PER_LAP) to (position - whole)
}

/** When the column [column] (0 = leftmost) starts: the left column first, each next one [staggerMs] later. */
internal fun columnStartMs(column: Int, delayMs: Int, staggerMs: Int): Int = delayMs + column * staggerMs

/**
 * An odometer: each digit column rolls in place from 0 past its target by [laps] extra laps (by place, ones
 * first; [lapsBeyond] for places the table does not reach), the left column first with [staggerMs] between
 * columns. A screen reader gets [contentDescription], the settled value, never a digit mid-roll. Reduced motion,
 * a preview and a settled composition show the final digits at once. A new [value] rolls again.
 */
@Composable
fun RollingDigits(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge.tabular(),
    color: Color = LocalContentColor.current,
    contentDescription: String = value.toString(),
    delayMs: Int = RecapMotion.CountHeroDelay,
    durationMs: Int = RecapMotion.CountHeroDuration,
    staggerMs: Int = RecapMotion.OdometerStagger,
    laps: List<Int> = RecapMotion.OdometerLaps,
    lapsBeyond: Int = RecapMotion.OdometerLapsBeyond,
) {
    val instant = rememberReducedMotion() || entranceSettled()
    val digits = digitsOf(value)
    val windowSp = style.fontSize.takeOrElse { 16.sp } * WINDOW_EM
    val windowDp = with(LocalDensity.current) { windowSp.toDp() }
    val digitStyle = style.copy(lineHeight = windowSp)
    key(value) {
        Row(modifier.clearAndSetSemantics { this.contentDescription = contentDescription }) {
            digits.forEachIndexed { column, digit ->
                DigitColumn(
                    digit = digit,
                    laps = lapsAt(digits.lastIndex - column, laps, lapsBeyond),
                    startMs = columnStartMs(column, delayMs, staggerMs),
                    durationMs = durationMs,
                    instant = instant,
                    style = digitStyle,
                    color = color,
                    windowHeight = windowDp,
                )
            }
        }
    }
}

@Composable
private fun DigitColumn(
    digit: Int,
    laps: Int,
    startMs: Int,
    durationMs: Int,
    instant: Boolean,
    style: TextStyle,
    color: Color,
    windowHeight: Dp,
) {
    val progress = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(progress, instant) {
        if (instant) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                1f,
                tween(durationMs, delayMillis = startMs, easing = MotionTokens.EasingEmphasizedDecelerate),
            )
        }
    }
    val (top, scrolled) = windowAt(odometerPosition(digit, laps, progress.value))
    Box(Modifier.height(windowHeight).clipToBounds()) {
        Text(
            top.toString(),
            style = style,
            color = color,
            modifier = Modifier.graphicsLayer { translationY = -scrolled * size.height },
        )
        Text(
            ((top + 1) % DIGITS_PER_LAP).toString(),
            style = style,
            color = color,
            modifier = Modifier.graphicsLayer { translationY = (1f - scrolled) * size.height },
        )
    }
}

// Previews — Studio tooling only. A preview never animates, so each shows the settled digits.
@PuzzleBoxPreviews
@Composable
internal fun RollingDigitsSettledPreview() {
    PuzzleBoxTheme { Box(Modifier.padding(Spacing.md)) { RollingDigits(value = 7L) } }
}

@PuzzleBoxPreviews
@Composable
internal fun RollingDigitsMultiDigitPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.md)) {
            RollingDigits(value = 70L)
            RollingDigits(value = 1_284L)
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun RollingDigitsDarkPreview() {
    PuzzleBoxTheme(darkTheme = true) { Box(Modifier.padding(Spacing.md)) { RollingDigits(value = 365L) } }
}
