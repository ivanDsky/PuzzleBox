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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens

// The push travels a twelfth of the width: enough to read as forward, not a full slide.
private const val PUSH_TRAVEL_DIVISOR = 12

/**
 * A block's entrance (06-a §Motion): a fade staggered by its [index], capped so a long
 * list never waits; [reduced] motion is one short fade. Previews show it settled.
 *
 * The fade is a graphicsLayer alpha, which composites into an offscreen buffer cut to the
 * layer's bounds, so anything the content paints outside its own bounds is clipped until
 * the fade ends. A block that does (a selection ring) gives that overhang as [bleed]: the
 * layer grows by it on every side while the block keeps its place and size in the layout.
 */
@Composable
internal fun EnterFade(
    index: Int,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    bleed: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
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
    val room = if (bleed > 0.dp) Modifier.bleedLayout(bleed) else Modifier
    val inset = if (bleed > 0.dp) Modifier.padding(bleed) else Modifier
    Box(modifier.then(room).graphicsLayer { this.alpha = alpha.value }.then(inset), content = content)
}

/**
 * Lets the node measure [room] larger on every side, then reports the original size and
 * places the node [room] up and left: the node's own bounds (and layer) hold the overhang
 * while its parent lays out the same footprint as without it.
 */
private fun Modifier.bleedLayout(room: Dp): Modifier = layout { measurable, constraints ->
    val r = room.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = 2 * r, vertical = 2 * r))
    layout((placeable.width - 2 * r).coerceAtLeast(0), (placeable.height - 2 * r).coerceAtLeast(0)) {
        placeable.place(-r, -r)
    }
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
