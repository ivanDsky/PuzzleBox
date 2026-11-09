package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min

/**
 * One Spotlight's timeline as pure functions of elapsed ms (prototype 07): dim and hairline Fade in, excluded marks
 * Scale in staggered, the callout Scale in, then one Pulse on a TARGET or CULPRIT. A step with neither (an
 * elimination) has no Pulse and ends as soon as its marks and callout have settled. Never longer than TOTAL_MS.
 */
internal class SpotlightSequence(spotlight: Spotlight) {
    private val onBoard: List<SpotlightRole> = spotlight.litCells().map(spotlight.roles::getValue)

    val pulses: Boolean = onBoard.any { it == SpotlightRole.TARGET || it == SpotlightRole.CULPRIT }

    val endMs: Int = if (pulses) {
        SpotlightTokens.PULSE_DELAY_MS + SpotlightTokens.PULSE_DURATION_MS
    } else {
        val excluded = onBoard.count { it == SpotlightRole.EXCLUDED }
        var end = SpotlightTokens.FADE_MS
        if (excluded > 0) end = max(end, excludedDelay(excluded - 1) + EXCLUDED_DURATION_MS)
        if (spotlight.callout != null) end = max(end, CALLOUT_DELAY_MS + CALLOUT_DURATION_MS)
        end
    }

    /** Dim and hairline opacity, 0 → 1. */
    fun fade(ms: Float): Float = progress(ms, 0, SpotlightTokens.FADE_MS, MotionTokens.EasingStandard)

    /** The [index]-th excluded mark (cells in index order), 0 → 1; scale is [scaleIn] from EXCLUDED_START_SCALE. */
    fun excluded(index: Int, ms: Float): Float =
        progress(ms, excludedDelay(index), EXCLUDED_DURATION_MS, MotionTokens.EasingEmphasizedDecelerate)

    /** The callout, 0 → 1; scale is [scaleIn] from CALLOUT_START_SCALE. */
    fun callout(ms: Float): Float =
        progress(ms, CALLOUT_DELAY_MS, CALLOUT_DURATION_MS, MotionTokens.EasingEmphasizedDecelerate)

    /** Pulse opacity: 0 until the Pulse starts, 1 once it peaks. */
    fun pulseAlpha(ms: Float): Float = if (pulses) min(1f, pulseRaw(ms) / PULSE_RISE_FRACTION) else 1f

    /** Pulse scale: 1 → PULSE_PEAK_SCALE at the rise fraction → 1. */
    fun pulseScale(ms: Float): Float {
        if (!pulses) return 1f
        val raw = pulseRaw(ms)
        val peak = SpotlightTokens.PULSE_PEAK_SCALE
        return if (raw <= PULSE_RISE_FRACTION) {
            lerp(1f, peak, MotionTokens.EasingStandard.transform(raw / PULSE_RISE_FRACTION))
        } else {
            lerp(peak, 1f, MotionTokens.EasingStandard.transform((raw - PULSE_RISE_FRACTION) / (1f - PULSE_RISE_FRACTION)))
        }
    }

    private fun pulseRaw(ms: Float): Float =
        ((ms - SpotlightTokens.PULSE_DELAY_MS) / SpotlightTokens.PULSE_DURATION_MS).coerceIn(0f, 1f)

    private fun excludedDelay(index: Int): Int =
        EXCLUDED_DELAY_MS + min(index, SpotlightTokens.EXCLUDED_MAX_STAGGERED) * SpotlightTokens.EXCLUDED_STAGGER_MS

    private fun progress(ms: Float, delay: Int, duration: Int, easing: Easing): Float =
        easing.transform(((ms - delay) / duration).coerceIn(0f, 1f))
}

/** A Scale in from [from] at [progress] 0 to 1 at progress 1. */
internal fun scaleIn(from: Float, progress: Float): Float = lerp(from, 1f, progress)

/**
 * Elapsed ms of the sequence playing for [playKey]: a new key restarts it from 0 in the same frame. Runs once to
 * [endMs] on a linear clock and stops (never infinite). Reduced motion, and previews, start and stay at [endMs].
 */
@Composable
internal fun rememberSequenceClock(playKey: Int, endMs: Int): State<Float> {
    val instant = rememberReducedMotion() || LocalInspectionMode.current
    val clock = remember(playKey) { Animatable(if (instant) endMs.toFloat() else 0f) }
    LaunchedEffect(clock, instant, endMs) {
        val remaining = endMs - clock.value.toInt()
        if (instant || remaining <= 0) {
            clock.snapTo(endMs.toFloat())
        } else {
            clock.animateTo(endMs.toFloat(), tween(remaining, easing = LinearEasing))
        }
    }
    return clock.asState()
}

// Prototype 07 timeline offsets that SpotlightTokens leaves implicit: the callout follows the dim in, the excluded
// marks follow the callout, and the Pulse peaks at 35% of its length (`@keyframes pulse1`).
private const val CALLOUT_DELAY_MS = 60
private const val CALLOUT_DURATION_MS = 120
private const val EXCLUDED_DELAY_MS = 100
private const val EXCLUDED_DURATION_MS = 100
private const val PULSE_RISE_FRACTION = 0.35f
