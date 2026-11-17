package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Prototype 141 (throwaway): a tap's fade through to another page, with no slide across the pages between (options B,
 * D and E). The page on show fades out over [MotionTokens.NavTransitionExit] on [MotionTokens.EasingLinear], the pager
 * snaps to the target, and that page fades in over the rest of [MotionTokens.DurationLong] on
 * [MotionTokens.EasingEmphasizedDecelerate]: the Community filter slot's clock. With a `travel` the two also move that
 * far along the direction of the change (a direction-aware fade through); with none they fade in place.
 *
 * Meanwhile [bar] glides the strip's indicator straight to the target over [MotionTokens.DurationMedium] on
 * [MotionTokens.EasingStandard], as the pagerless strip does, and the link runs on the pages' clock: its share is
 * half done when the old page is gone, so a pane that follows it changes with the list, not before or after it. While
 * [active] only page [shown] is drawn, at [alpha] and shifted by [shift] pixels.
 *
 * Reduced motion's tap is [crossfade]: page [arriving] is drawn over page [shown] where it stands, at the rest of
 * [alpha], and the bar jumps.
 */
@Stable
internal class TabJump {
    var active by mutableStateOf(false)
        private set
    var shown by mutableIntStateOf(-1)
        private set
    var arriving by mutableIntStateOf(-1)
        private set
    var alpha by mutableFloatStateOf(1f)
        private set
    var shift by mutableFloatStateOf(0f)
        private set
    val bar = Animatable(0f)

    /** One crossfade in place from the page on show straight to [to], over [MotionTokens.ReducedFadeDuration]. */
    suspend fun crossfade(state: PagerState, link: TabSwipeLink?, to: Int) {
        val from = state.currentPage
        try {
            bar.snapTo(to.toFloat())
            shown = from
            arriving = to
            alpha = 1f
            shift = 0f
            active = true
            link?.jump(from, to)
            animate(0f, 1f, animationSpec = tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear)) { share, _ ->
                alpha = 1f - share
                link?.jumpShare(share)
            }
            state.scrollToPage(to)
        } finally {
            active = false
            shown = -1
            arriving = -1
            link?.land()
        }
    }

    suspend fun run(state: PagerState, link: TabSwipeLink?, to: Int, travel: Float) {
        val from = state.currentPage
        val toward = if (to > from) 1f else -1f
        try {
            coroutineScope {
                bar.snapTo(from.toFloat())
                shown = from
                alpha = 1f
                shift = 0f
                active = true
                link?.jump(from, to)
                launch { bar.animateTo(to.toFloat(), tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)) }
                animate(0f, 1f, animationSpec = tween(MotionTokens.NavTransitionExit, easing = MotionTokens.EasingLinear)) { gone, _ ->
                    alpha = 1f - gone
                    shift = -toward * travel * gone
                    link?.jumpShare(gone / 2f)
                }
                shown = to
                alpha = 0f
                shift = toward * travel
                state.scrollToPage(to)
                val enter = tween<Float>(
                    durationMillis = MotionTokens.DurationLong - MotionTokens.NavTransitionExit,
                    easing = MotionTokens.EasingEmphasizedDecelerate,
                )
                animate(0f, 1f, animationSpec = enter) { here, _ ->
                    alpha = here
                    shift = toward * travel * (1f - here)
                    link?.jumpShare(0.5f + here / 2f)
                }
            }
        } finally {
            active = false
            shown = -1
            link?.land()
        }
    }
}
