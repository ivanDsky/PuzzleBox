package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** A tap's change: from [from], toward the tab [to], which is [toward] (-1 or 1) of it on the line of pages. */
internal class TapFade(val from: Int, val to: Int, val toward: Int)

/**
 * Runs a tap, or a host's request for another tab, as the pager's fade through on a clock ([TabFadeLink.share]): the
 * page it leaves fades out over [MotionTokens.NavTransitionExit] on a line, then the pager jumps to the target
 * with nothing showing, and the target fades in over the rest of [MotionTokens.DurationLong]. A tab in between is never
 * composed. The bar glides on its own over [MotionTokens.DurationMedium]. Under reduced motion both halves are
 * [MotionTokens.ReducedFadeDuration] together and the bar is the pager's to jump.
 *
 * A change started while another runs takes over from the page that shows most, at the share its fade has reached, so
 * a page's alpha is continuous and three quick taps rest on the last tab. A finger that takes the pager ends it
 * ([interrupt]): the drag goes on from the page the pager is on.
 */
internal class TabTapRunner(
    private val link: TabFadeLink,
    private val state: PagerState,
    private val scope: CoroutineScope,
) {
    /** Where the bar is during a tap, on the line of tabs. */
    val bar = Animatable(0f)

    private var fade: Job? = null
    private var glide: Job? = null

    fun go(to: Int, reduced: Boolean) {
        val running = link.tap
        if (running != null && to == running.to) return
        val share = link.share
        val from = when {
            running == null -> state.currentPage
            share < TabFadeLink.TAP_HALF -> running.from
            else -> running.to
        }
        val begin = when {
            running == null -> abs(state.currentPageOffsetFraction).coerceAtMost(TabFadeLink.TAP_HALF)
            share < TabFadeLink.TAP_HALF -> share
            else -> 1f - share
        }
        if (to == from && running == null) return
        val toward = if (to == from) checkNotNull(running).toward else sign((to - from).toFloat()).toInt()
        fade?.cancel()
        link.tap = TapFade(from, to, toward)
        link.share = begin
        glideBar(to, reduced, running != null)
        fade = scope.launch(start = CoroutineStart.UNDISPATCHED) { fadeThrough(from, to, begin, reduced) }
    }

    /** A finger took the pager: ends a running change where it is, with the pager on the page it has reached. */
    fun interrupt() {
        if (link.tap == null) return
        fade?.cancel()
        glide?.cancel()
        link.tap = null
        link.share = 0f
    }

    private suspend fun fadeThrough(from: Int, to: Int, begin: Float, reduced: Boolean) {
        val out = if (reduced) MotionTokens.ReducedFadeDuration / 2 else MotionTokens.NavTransitionExit
        val into = if (reduced) MotionTokens.ReducedFadeDuration / 2 else MotionTokens.DurationLong - MotionTokens.NavTransitionExit
        if (to == from) {
            run(begin, 0f, (out * begin / TabFadeLink.TAP_HALF).toInt(), MotionTokens.EasingLinear)
        } else {
            if (state.currentPage != from || state.currentPageOffsetFraction != 0f) state.scrollToPage(from)
            if (begin < TabFadeLink.TAP_HALF) {
                run(begin, TabFadeLink.TAP_HALF, (out * (TabFadeLink.TAP_HALF - begin) / TabFadeLink.TAP_HALF).toInt(), MotionTokens.EasingLinear)
            }
            // Nothing shows here: the pager moves to the target while it is as faded as the page it leaves.
            link.share = TabFadeLink.TAP_HALF
            state.scrollToPage(to)
            run(TabFadeLink.TAP_HALF, 1f, into, if (reduced) MotionTokens.EasingLinear else MotionTokens.EasingEmphasizedDecelerate)
        }
        link.tap = null
        link.share = 0f
    }

    private suspend fun run(from: Float, to: Float, millis: Int, easing: Easing) {
        animate(from, to, animationSpec = tween(millis, easing = easing)) { value, _ -> link.share = value }
    }

    private fun glideBar(to: Int, reduced: Boolean, tapping: Boolean) {
        glide?.cancel()
        if (reduced) return
        glide = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!tapping) bar.snapTo(state.currentPage + state.currentPageOffsetFraction)
            bar.animateTo(to.toFloat(), tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
        }
    }
}
