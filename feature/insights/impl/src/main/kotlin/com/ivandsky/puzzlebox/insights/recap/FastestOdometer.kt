package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The slam's timer as odometer columns (the prototype's `odoGo`). Each column is a strip of a blank cell and three laps
 * of 0-9, and [cells] holds where each stands: 0 the blank, 1 + d a d. The columns are laid out for the wider of the time
 * and the average, so the climb to the average and the drop to the time roll in the same columns; the leading columns
 * the time leaves blank (and the hour colon before them) collapse once it lands. [settled] hands over to plain text.
 */
internal class Odometer(private val timeMs: Long, averageMs: Long?, instant: Boolean) {
    private val widest = max(timeMs, averageMs ?: 0L)
    val hours = widest >= HOUR_MS
    private val minuteColumns = if (hours || widest / MINUTE_MS >= 10) 2 else 1
    private val columns = (if (hours) 1 else 0) + minuteColumns + 2

    /** The leading columns the time leaves blank. */
    private val blanks = cellsOf(timeMs).takeWhile { it == 0 }.size
    val cells = cellsOf(if (instant) timeMs else 0L).map { Animatable(it.toFloat()) }
    val hourColon = Animatable(if (instant && blanks == 0) 1f else 0f)
    val collapse = Animatable(if (instant) 1f else 0f)
    var settled by mutableStateOf(instant)

    /**
     * Rolls every column to [ms]'s digit, the left column first and each [staggerMs] after the last: up, a column laps
     * at least once past its target (the ones twice); down, it falls back to the nearest lap below, with [over] cells of
     * overshoot. Returns once the last column stops.
     */
    suspend fun roll(ms: Long, up: Boolean, durationMs: Int, staggerMs: Int, easing: Easing, over: Float = 0f) = coroutineScope {
        val to = cellsOf(ms)
        cells.forEachIndexed { j, cell ->
            val from = cell.targetValue.roundToInt()
            val target = target(from, to[j], columns - 1 - j, up)
            if (target != from) {
                val curve = if (over == 0f) easing else overshoot(easing, 1f + over / abs(target - from))
                launch { cell.animateTo(target.toFloat(), tween(durationMs, staggerMs * j, curve)) }
            }
        }
    }

    /** The time has landed: its leading blank columns (and the hour colon) fold away, then the text takes over. */
    suspend fun land() = coroutineScope {
        if (hours && blanks > 0) launch { hourColon.animateTo(0f, tween(MotionTokens.DurationShort, easing = MotionTokens.EasingLinear)) }
        if (blanks > 0) collapse.animateTo(1f, tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
        settled = true
    }

    /** The columns' cells for [ms]: the hour, the minutes (blank where the time has none) and the seconds. */
    private fun cellsOf(ms: Long): List<Int> {
        val total = ms / 1000
        val h = (total / 3600).toInt()
        val m = (total % 3600 / 60).toInt()
        val s = (total % 60).toInt()
        val lead = when {
            hours -> listOf(h.takeIf { h > 0 }, (m / 10).takeIf { h > 0 || m >= 10 }, m % 10)
            minuteColumns == 2 -> listOf((m / 10).takeIf { m >= 10 }, m % 10)
            else -> listOf(m)
        }
        return (lead + listOf(s / 10, s % 10)).map { if (it == null) 0 else 1 + it }
    }

    private fun target(from: Int, cell: Int, place: Int, up: Boolean): Int {
        if (cell == 0) return 0
        val d = cell - 1
        return if (up) {
            val laps = min(if (place == 0) 20 else 10, LAST_CELL - 1)
            val t = 1 + d + 10 * -Math.floorDiv(-(from + laps - 1 - d), 10)
            if (t > LAST_CELL) 1 + d + 10 * Math.floorDiv(LAST_CELL - 1 - d, 10) else t
        } else {
            val t = 1 + d + 10 * Math.floorDiv(from - 2 - d, 10)
            if (t < 1 || t > from - 1) 1 + d else t
        }
    }

    /** The width the columns take and, with [draw], each drawn where it stands: digits by [glyphs], the colon last. */
    fun walk(glyphs: List<TextLayoutResult>, scope: DrawScope? = null, color: Color = Color.Unspecified): Float {
        val digit = glyphs[0].getLineRight(0)
        val colon = glyphs[COLON].getLineRight(0)
        val fold = 1f - collapse.value
        var x = 0f
        for (j in 0 until columns) {
            if (j == columns - 2) {
                scope?.drawText(glyphs[COLON], color, Offset(x, 0f))
                x += colon
            }
            scope?.drawColumn(glyphs, cells[j].value, x, digit, color)
            x += if (j < blanks) digit * fold else digit
            if (hours && j == 0) {
                scope?.drawText(glyphs[COLON], color, Offset(x, 0f), alpha = hourColon.value)
                x += if (blanks > 0) colon * fold else colon
            }
        }
        return x
    }

    /** One column at strip position [at]: the cells either side of it, clipped to the column's line (a little wider). */
    private fun DrawScope.drawColumn(glyphs: List<TextLayoutResult>, at: Float, x: Float, width: Float, color: Color) {
        val height = size.height
        val bleed = width * CLIP_BLEED
        val first = floor(at).toInt()
        clipRect(left = x - bleed, top = 0f, right = x + width + bleed, bottom = height) {
            for (k in first..first + 1) {
                if (k in 1..LAST_CELL) drawText(glyphs[(k - 1) % 10], color, Offset(x, (k - at) * height))
            }
        }
    }
}

/**
 * [odometer] drawn in [style]: one line as tall as the settled text's, so the hand-over to [text] does not move a
 * pixel. A screen reader gets [text], the time it lands on.
 */
@Composable
internal fun OdometerText(odometer: Odometer, text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val glyphs = remember(style, measurer) { (DIGITS + ":").map { measurer.measure(it.toString(), style, softWrap = false, maxLines = 1) } }
    Spacer(
        modifier
            .clearAndSetSemantics { contentDescription = text }
            .layout { measurable, _ ->
                val width = odometer.walk(glyphs).roundToInt()
                val height = glyphs[0].size.height
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .drawBehind { odometer.walk(glyphs, this, color) },
    )
}

/** [easing] through the prototype's overshoot keyframe: past the target to [peak] at [OvershootAt] of the eased run. */
private fun overshoot(easing: Easing, peak: Float) = Easing { x ->
    val e = easing.transform(x)
    if (e < OvershootAt) e / OvershootAt * peak else peak + (1f - peak) * (e - OvershootAt) / (1f - OvershootAt)
}

private const val HOUR_MS = 3_600_000L
private const val MINUTE_MS = 60_000L
private const val DIGITS = "0123456789"
private const val COLON = 10

/** The strip's last cell: a blank and three laps of 0-9. */
private const val LAST_CELL = 30

/** The prototype's column clip (`inset(0 -.1em)`), as a share of a digit's width. */
private const val CLIP_BLEED = 0.16f
private const val OvershootAt = 0.78f
