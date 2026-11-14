package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween

/** Plays the trace pop ([MotionTokens.Trace]) from the current value: up to PopScale, then back to 1. */
suspend fun Animatable<Float, AnimationVector1D>.tracePop() {
    val rise = (MotionTokens.Trace.PopDuration * MotionTokens.Trace.PopRiseFraction).toInt()
    animateTo(MotionTokens.Trace.PopScale, tween(rise, easing = MotionTokens.Trace.EasingOut))
    animateTo(1f, tween(MotionTokens.Trace.PopDuration - rise, easing = MotionTokens.EasingStandard))
}

/** The trace pop's scale at [t] (0..1, clamped) through PopDuration, for frame-driven cells. */
fun tracePopScale(t: Float): Float {
    val p = t.coerceIn(0f, 1f)
    val rise = MotionTokens.Trace.PopRiseFraction
    val peak = MotionTokens.Trace.PopScale
    return if (p < rise) {
        1f + (peak - 1f) * MotionTokens.Trace.EasingOut.transform(p / rise)
    } else {
        peak + (1f - peak) * MotionTokens.EasingStandard.transform((p - rise) / (1f - rise))
    }
}
