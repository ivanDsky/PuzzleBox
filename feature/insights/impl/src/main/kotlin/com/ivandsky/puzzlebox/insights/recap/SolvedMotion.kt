package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/**
 * A count's landing (Stamp): this modifier scales it from [MotionTokens.StampFromScale] to 1 on the Stamp spring about its
 * bottom-right corner while it fades in over [MotionTokens.StampFadeDuration], both starting [delayMs] after the card is
 * shown. Reduced motion and a preview are at rest at once (the page's one fade stands in).
 */
@Composable
internal fun Modifier.solvedStamp(delayMs: Int): Modifier {
    val instant = rememberReducedMotion() || entranceSettled()
    val scale = remember { Animatable(if (instant) 1f else MotionTokens.StampFromScale) }
    val fade = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(scale, instant) {
        if (instant) {
            scale.snapTo(1f)
            fade.snapTo(1f)
        } else {
            delay(delayMs.toLong())
            launch { fade.animateTo(1f, tween(MotionTokens.StampFadeDuration, easing = LinearEasing)) }
            scale.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = fade.value
        transformOrigin = TransformOrigin(1f, 1f)
    }
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

/** The stripe's count starts this long after the stripe does, and its second line comes in [StripeSubDelayMs] after it. */
internal const val StripeCountDelayMs = 100
internal const val StripeSubDelayMs = 260
private const val BandFirstMs = 650
private const val BandStepMs = 90
private const val StripeAfterCountMs = 900
