package com.ivandsky.puzzlebox.app.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEvent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.play.api.ResultKey
import kotlin.math.roundToInt

/**
 * Every navigation motion value (A19, A20) in one place, built from [MotionTokens]. The
 * HITL round on the Tab S9 tunes these; nothing below hardcodes a duration or a distance.
 */
internal object NavMotion {
    /** Shared-axis X push: both screens travel together for this long. */
    val AxisTravelMillis = MotionTokens.DurationLong

    /** Travel distance as a fraction of the container width. A/B: 1/12 of the width vs 30 dp. */
    const val AxisTravelFraction = 1f / 12f

    /** Fade-through: the outgoing screen is gone before the incoming one appears, so two
     *  screens' contents never superimpose. Shared by push/pop, tab switch and reduced motion. */
    const val FadeOutMillis = MotionTokens.NavTransitionExit
    const val FadeInDelayMillis = MotionTokens.NavTransitionEnterDelay
    const val FadeInMillis = MotionTokens.NavTransitionEnter

    /** Back (every pop, and the settle after a predictive-back release) is quicker than
     *  forward: the same shapes on a shorter clock. */
    const val PopTravelMillis = MotionTokens.NavPopTravel
    const val PopFadeOutMillis = MotionTokens.NavPopExit
    const val PopFadeInDelayMillis = MotionTokens.NavPopEnterDelay
    const val PopFadeInMillis = MotionTokens.NavPopEnter

    /** Result's scale-in: one critically damped ~0.3 s spring. Back never scales: a Result
     *  pops like any screen (S9 HITL: a visible shrink on back read as slow). */
    const val ResultScale = 0.92f
    const val ResultDamping = MotionTokens.SpringDampingDefault
    const val ResultStiffness = MotionTokens.SpringStiffnessDefault

    /** Predictive back scrubs this timeline linearly with the gesture's progress; NavDisplay
     *  settles the remainder from the live value on commit or cancel, on the back clock. */
    const val PredictiveMillis = PopTravelMillis

    /** Outgoing screen's shift toward the gesture, as a fraction of the width. */
    const val PredictiveShiftFraction = 0.08f

    /** Destination beneath the preview starts this opaque and reaches 1 at commit. */
    const val PredictiveDestinationAlpha = 0.6f

    /** The outgoing screen fades out over the last stretch of the timeline. */
    val PredictiveFadeOutMillis = MotionTokens.DurationMedium / 2
}

/**
 * The shell's one push/pop chooser over NavDisplay scene metadata ([OWNER_TAB],
 * [ENTRY_KEY]); [from] is the outgoing entry's, [to] the incoming one's.
 * - Reduced motion: an opacity-only fade-through for every case (A20).
 * - Owner tab differs (tab switch, or back from a tab root to Home): fade-through, no
 *   direction (A19).
 * - Result: a push scales it in; its pop is an ordinary back.
 * - Otherwise shared-axis X: forward enters from the end edge, back from the start edge.
 */
internal fun navTransition(
    from: Map<String, Any>,
    to: Map<String, Any>,
    pop: Boolean,
    reduceMotion: Boolean,
    layoutDirection: LayoutDirection,
): ContentTransform = when {
    reduceMotion || from[OWNER_TAB] != to[OWNER_TAB] -> fadeThrough(pop)
    !pop && to[ENTRY_KEY] is ResultKey -> resultEntrance()
    else -> sharedAxisX(forward = !pop, layoutDirection)
}

/**
 * Predictive back: a gesture-scrubbed preview of the real destination. Within a tab the
 * outgoing screen shifts with the swipe (no scale) ([swipeEdge], a [NavigationEvent] edge)
 * over the destination; across tabs, or under reduced motion, the scrub is opacity only.
 */
internal fun predictivePopTransition(
    from: Map<String, Any>,
    to: Map<String, Any>,
    swipeEdge: Int,
    reduceMotion: Boolean,
): ContentTransform {
    val scrub = tween<Float>(NavMotion.PredictiveMillis, easing = LinearEasing)
    val destination = fadeIn(scrub, initialAlpha = NavMotion.PredictiveDestinationAlpha)
    if (reduceMotion || from[OWNER_TAB] != to[OWNER_TAB]) {
        return destination togetherWith fadeOut(scrub)
    }
    // A swipe from the right edge pulls the screen left; from the left (or no edge), right.
    val toward = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
    val shift = slideOutHorizontally(tween<IntOffset>(NavMotion.PredictiveMillis, easing = LinearEasing)) {
        toward * (it * NavMotion.PredictiveShiftFraction).roundToInt()
    }
    val lateFade = fadeOut(
        tween(
            durationMillis = NavMotion.PredictiveFadeOutMillis,
            delayMillis = NavMotion.PredictiveMillis - NavMotion.PredictiveFadeOutMillis,
            easing = LinearEasing,
        ),
    )
    return destination togetherWith (shift + lateFade)
}

private fun fadeThrough(pop: Boolean): ContentTransform =
    fadeIn(fadeInSpec(pop)) togetherWith fadeOut(fadeOutSpec(pop))

private fun sharedAxisX(forward: Boolean, layoutDirection: LayoutDirection): ContentTransform {
    // +1 points at the end edge in screen coordinates; the incoming screen starts there on a
    // push and at the start edge on a pop, and the outgoing screen travels the other way.
    val endward = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    val from = if (forward) endward else -endward
    val pop = !forward
    val millis = if (pop) NavMotion.PopTravelMillis else NavMotion.AxisTravelMillis
    val travel = tween<IntOffset>(millis, easing = MotionTokens.EasingStandard)
    val distance = { width: Int -> (width * NavMotion.AxisTravelFraction).roundToInt() }
    return (slideInHorizontally(travel) { from * distance(it) } + fadeIn(fadeInSpec(pop))) togetherWith
        (slideOutHorizontally(travel) { -from * distance(it) } + fadeOut(fadeOutSpec(pop)))
}

private fun resultEntrance(): ContentTransform =
    (fadeIn(resultSpring()) + scaleIn(resultSpring(), initialScale = NavMotion.ResultScale)) togetherWith
        fadeOut(fadeOutSpec(pop = false))


private fun fadeInSpec(pop: Boolean): FiniteAnimationSpec<Float> = tween(
    durationMillis = if (pop) NavMotion.PopFadeInMillis else NavMotion.FadeInMillis,
    delayMillis = if (pop) NavMotion.PopFadeInDelayMillis else NavMotion.FadeInDelayMillis,
    easing = MotionTokens.EasingEmphasizedDecelerate,
)

private fun fadeOutSpec(pop: Boolean): FiniteAnimationSpec<Float> = tween(
    if (pop) NavMotion.PopFadeOutMillis else NavMotion.FadeOutMillis,
    easing = MotionTokens.EasingEmphasizedAccelerate,
)

private fun resultSpring(): FiniteAnimationSpec<Float> =
    spring(dampingRatio = NavMotion.ResultDamping, stiffness = NavMotion.ResultStiffness)
