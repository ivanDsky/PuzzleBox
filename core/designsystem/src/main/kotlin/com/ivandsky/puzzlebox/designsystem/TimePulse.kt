package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.math.PI
import kotlin.math.sin

/** The scale at [progress] (0..1) of one pulse: 1 up to [MotionTokens.PulseScale] and back to 1. */
internal fun pulseScale(progress: Float): Float = 1f + (MotionTokens.PulseScale - 1f) * sin(progress * PI.toFloat())

/**
 * Scales the block to [MotionTokens.PulseScale] and back over [MotionTokens.PulseDuration] once
 * [trigger] turns true. Reduced motion and previews: none. Draw-phase only.
 */
fun Modifier.timePulse(trigger: Boolean): Modifier = composed {
    val skip = rememberReducedMotion() || LocalInspectionMode.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger, skip) {
        if (!trigger || skip) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(MotionTokens.PulseDuration, easing = LinearEasing))
    }
    graphicsLayer {
        val scale = pulseScale(progress.value)
        scaleX = scale
        scaleY = scale
    }
}
