package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring

// Canonical motion vocabulary (A12): the app animates with shared-axis screen
// transitions, a container transform into a puzzle, and spring physics on
// interactions. Transitions are wired in later tickets; they consume these tokens
// so every screen moves on the same physics.
object MotionTokens {
    // Feedback stays under ~160ms; only full-screen patterns go longer.
    val DurationShort = 120
    val DurationMedium = 240
    val DurationLong = 300
    val DurationExpressive = 400

    // Material 3 emphasized curves: decelerate on the way in, accelerate on the way out.
    val EasingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val EasingLinear = LinearEasing

    // Critically damped by default (no overshoot); the underdamped variant is reserved
    // for gestures that carry momentum (flick, drag release). Stiffness ~380 matches an
    // Apple-style response of ~0.32s: quick without feeling twitchy.
    const val SpringDampingDefault = Spring.DampingRatioNoBouncy
    const val SpringDampingMomentum = 0.8f
    const val SpringStiffnessDefault = 380f
}

// A named screen-level pattern. Exit is faster than enter: leaving feels snappy,
// arriving feels intentional.
data class MotionPattern(
    val name: String,
    val description: String,
    val enterDurationMillis: Int,
    val enterEasing: Easing,
    val exitDurationMillis: Int,
    val exitEasing: Easing,
)

val SharedAxisTransition = MotionPattern(
    name = "Shared axis",
    description = "Screen-to-screen navigation: old and new screens slide along one axis, staggered so the move reads as one gesture.",
    enterDurationMillis = MotionTokens.DurationLong,
    enterEasing = MotionTokens.EasingEmphasizedDecelerate,
    exitDurationMillis = MotionTokens.DurationMedium,
    exitEasing = MotionTokens.EasingEmphasizedAccelerate,
)

val ContainerTransform = MotionPattern(
    name = "Container transform",
    description = "A puzzle card grows into its full screen while the subject stays visible, connecting list and game.",
    enterDurationMillis = MotionTokens.DurationExpressive,
    enterEasing = MotionTokens.EasingStandard,
    exitDurationMillis = MotionTokens.DurationLong,
    exitEasing = MotionTokens.EasingStandard,
)
