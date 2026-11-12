package com.ivandsky.puzzlebox.play

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * One clock for every fixed-height [Slot] of a card, so a change of [shown] fades all of them
 * together over [MotionTokens.DurationExpressive]. Slots that each ran their own short fade, with the
 * action row left out, read as the card flickering; one transition keeps the lines and the
 * action changing as one beat, and the frame never moves. Reduced motion has no transition, so
 * a slot draws [shown] at once.
 */
internal class DayFade<T>(private val transition: Transition<T>?, private val shown: T) {

    // Transition.Crossfade is the one way to fade several slots on a single clock.
    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun Slot(height: Dp, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.CenterStart) {
            if (transition == null) {
                content(shown)
            } else {
                transition.Crossfade(
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(MotionTokens.DurationExpressive, easing = MotionTokens.EasingStandard),
                ) { face ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { content(face) }
                }
            }
        }
    }
}

@Composable
internal fun <T> rememberDayFade(shown: T): DayFade<T> {
    val reducedMotion = rememberReducedMotion()
    val transition = if (reducedMotion) null else updateTransition(shown, label = "day-card")
    return DayFade(transition, shown)
}
