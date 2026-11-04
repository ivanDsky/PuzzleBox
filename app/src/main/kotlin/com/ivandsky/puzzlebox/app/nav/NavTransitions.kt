package com.ivandsky.puzzlebox.app.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import com.ivandsky.puzzlebox.designsystem.MotionTokens

// Fade-through screen transition: 90ms accelerated fade-out, then the incoming screen
// fades in over 200ms (delayed 90ms) with a residual 1/24 slide. Sequencing guarantees
// only one screen's content is ever visible; every scene root paints an opaque canvas
// (SceneCanvas) so nothing underneath can bleed through. Tokens keep one vocabulary.
internal fun AnimatedContentTransitionScope<*>.screenEnterTransform() =
    fadeIn(
        tween(MotionTokens.NavTransitionEnter, delayMillis = MotionTokens.NavTransitionEnterDelay, easing = MotionTokens.EasingEmphasizedDecelerate),
    ) + slideInHorizontally(
        tween(MotionTokens.NavTransitionEnter, delayMillis = MotionTokens.NavTransitionEnterDelay, easing = MotionTokens.EasingEmphasizedDecelerate),
    ) { it / 24 }

internal fun AnimatedContentTransitionScope<*>.screenExitTransform() =
    fadeOut(tween(MotionTokens.NavTransitionExit, easing = MotionTokens.EasingEmphasizedAccelerate))

// Ticket 23 — the run's ONLY new animation (spec A3): the result screen enters with a
// fade-through — opacity 0→1 + scale 0.92→1 on ONE critically damped spring (~0.3s
// response, MotionTokens.SpringStiffnessDefault). Reduced motion (animator scale 0)
// keeps the opacity half only. The outgoing screen still fades out in its own 90ms, so
// the arrival reads as a fade-through, not a crossfade.
internal fun resultEnterTransform(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedDecelerate))
    } else {
        val enter = spring<Float>(
            dampingRatio = MotionTokens.SpringDampingDefault,
            stiffness = MotionTokens.SpringStiffnessDefault,
        )
        fadeIn(enter) + scaleIn(initialScale = 0.92f, animationSpec = enter)
    }
