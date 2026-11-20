package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * How far (0..1) a Solved entrance move has come: it starts [delayMs] after the card is shown and runs [durationMs] on
 * EmphasizedDecelerate. Reduced motion and a preview are at 1 at once. The deck re-creates the face on a replay, so the
 * move plays again.
 */
@Composable
internal fun rememberSolvedMove(delayMs: Int, durationMs: Int = MotionTokens.DurationExpressive): Float {
    val instant = rememberReducedMotion() || entranceSettled()
    val progress = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(progress, instant) {
        if (instant) progress.snapTo(1f) else progress.animateTo(1f, tween(durationMs, delayMillis = delayMs, easing = MotionTokens.EasingEmphasizedDecelerate))
    }
    return progress.value
}

/** The card's own 120ms fade in when motion is reduced (every other part is already at its final value); 1 otherwise. */
@Composable
internal fun rememberReducedFade(): Float {
    val fade = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(reduced) { if (reduced) fade.animateTo(1f, tween(MotionTokens.ReducedFadeDuration)) else fade.snapTo(1f) }
    return if (reduced) fade.value else 1f
}

/** When band [index] of a sheet starts to slide in, ms after the card is shown (Slide: 650 + k × 90). */
internal fun bandStartMs(index: Int): Int = BandFirstMs + index * BandStepMs

/** When the stripe starts: 900ms after the count of the last of [bands] bands starts. */
internal fun stripeStartMs(bands: Int): Int = bandStartMs(bands - 1) + BandCountDelayMs + StripeAfterCountMs

internal const val BandCountDelayMs = 220
private const val BandFirstMs = 650
private const val BandStepMs = 90
private const val StripeAfterCountMs = 900
