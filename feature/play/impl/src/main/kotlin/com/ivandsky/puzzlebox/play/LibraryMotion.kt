package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.ivandsky.puzzlebox.designsystem.MotionTokens

// The push travels a twelfth of the width: enough to read as forward, not a full slide.
private const val PUSH_TRAVEL_DIVISOR = 12

/**
 * A block's entrance (06-a §Motion): a fade staggered by its [index], capped so a long
 * list never waits; [reduced] motion is one short fade. Previews show it settled.
 */
@Composable
internal fun EnterFade(index: Int, reduced: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val settled = LocalInspectionMode.current
    val alpha = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(alpha) {
        val spec = if (reduced) {
            tween<Float>(MotionTokens.ReducedFadeDuration)
        } else {
            tween(
                durationMillis = MotionTokens.EnterDuration,
                delayMillis = index.coerceIn(0, MotionTokens.EnterStaggerCap) * MotionTokens.EnterStagger,
                easing = MotionTokens.EasingEmphasizedDecelerate,
            )
        }
        alpha.animateTo(1f, spec)
    }
    Box(modifier.graphicsLayer { this.alpha = alpha.value }, content = content)
}

/**
 * The phone's list ⇄ page switch, shared axis X like the app's own pushes: the page comes
 * in from the end on open (true) and the list from the start on back, on the nav tokens.
 */
internal fun AnimatedContentTransitionScope<Boolean>.libraryPush(reduced: Boolean): ContentTransform {
    if (reduced) {
        return fadeIn(tween(MotionTokens.ReducedFadeDuration)) togetherWith fadeOut(tween(MotionTokens.ReducedFadeDuration))
    }
    val forward = targetState
    val sign = if (forward) 1 else -1
    val enter = if (forward) {
        fadeIn(tween(MotionTokens.NavTransitionEnter, MotionTokens.NavTransitionEnterDelay)) +
            slideInHorizontally(tween(MotionTokens.NavTransitionEnter + MotionTokens.NavTransitionEnterDelay)) {
                sign * it / PUSH_TRAVEL_DIVISOR
            }
    } else {
        fadeIn(tween(MotionTokens.NavPopEnter, MotionTokens.NavPopEnterDelay)) +
            slideInHorizontally(tween(MotionTokens.NavPopTravel)) { sign * it / PUSH_TRAVEL_DIVISOR }
    }
    val exitDuration = if (forward) MotionTokens.NavTransitionExit else MotionTokens.NavPopExit
    val travel = if (forward) MotionTokens.NavTransitionEnter + MotionTokens.NavTransitionEnterDelay else MotionTokens.NavPopTravel
    val exit = fadeOut(tween(exitDuration)) +
        slideOutHorizontally(tween(travel)) { -sign * it / PUSH_TRAVEL_DIVISOR }
    return enter togetherWith exit
}
