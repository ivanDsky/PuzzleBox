package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * One fade-through clock for every fixed-height [Slot] of a card: the old day fades out over
 * [MotionTokens.DurationExit] on the accelerate easing, and only then the new day fades in over
 * [MotionTokens.DurationLong] on the decelerate easing, rising [MotionTokens.EnterRise] into its
 * place. The two never overlap: a crossfade drew both days' text on top of each other, and the
 * standard easing front-loaded it, so it read as a blink. Slots clip the rise, so the frame never
 * moves. Reduced motion draws the new day at once.
 */
internal class DayFade<T>(
    /** The day the slots draw right now: the old one until its exit ends. */
    val drawn: T,
    private val level: Animatable<Float, AnimationVector1D>?,
    private val entering: State<Boolean>,
) {
    /** How much of [drawn] shows, 0 to 1. */
    val opacity: Float get() = level?.value ?: 1f

    /** How far below its place [drawn] sits: the enter's rise, shrinking to nothing as it fades in. */
    val rise: Dp get() = if (entering.value) MotionTokens.EnterRise * (1f - opacity) else 0.dp

    @Composable
    fun Slot(height: Dp, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
        Box(modifier.fillMaxWidth().height(height).clipToBounds()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = opacity
                    translationY = rise.toPx()
                },
                contentAlignment = Alignment.CenterStart,
            ) { content(drawn) }
        }
    }
}

@Composable
internal fun <T> rememberDayFade(shown: T): DayFade<T> {
    val reducedMotion = rememberReducedMotion()
    var drawn by remember { mutableStateOf(shown) }
    val level = remember { Animatable(1f) }
    val entering = remember { mutableStateOf(false) }
    LaunchedEffect(shown, reducedMotion) {
        if (reducedMotion) {
            drawn = shown
            entering.value = false
            level.snapTo(1f)
            return@LaunchedEffect
        }
        if (drawn != shown) {
            entering.value = false
            level.animateTo(0f, tween(MotionTokens.DurationExit, easing = MotionTokens.EasingEmphasizedAccelerate))
            drawn = shown
            entering.value = true
        }
        // A day that returns before its exit ends fades back in without a rise: it never left.
        level.animateTo(1f, tween(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasizedDecelerate))
        entering.value = false
    }
    return DayFade(if (reducedMotion) shown else drawn, if (reducedMotion) null else level, entering)
}
