package com.ivandsky.puzzlebox.app.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEvent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.roundToInt

/** Scene metadata carries the entry's key as `Any`; these read it as the [NavKey] it is. */
private fun Any?.isBoardEntry(): Boolean = (this as? NavKey)?.let { movesLikeGame(it) && !isResultKey(it) } == true

private fun Any?.isResultEntry(): Boolean = (this as? NavKey)?.let { isResultKey(it) } == true

/** Which navigation chrome the window wears: a bottom bar (phone, B) or a rail (tablet, D). */
internal enum class NavForm { Phone, Tablet }

internal enum class NavMotionKind {
    /** B: the upper page travels the full width; the lower one parallaxes under a scrim. */
    Slide,

    /** D: the game grows out of its card (container transform); both scenes hold opaque. */
    Lift,

    /** D: shared axis X at full width, both pages opaque and travelling together. */
    Axis,

    /** D: the incoming tab fades in and rises over the outgoing one, which stays opaque. */
    Rise,

    /** D with TabletCrossfade: the upper page fades in place over the opaque lower one; nothing travels or scales. */
    Crossfade,

    /** B tab switch: out, then in. */
    FadeThrough,

    /** Any move under reduced motion: the upper page fades, nothing travels. */
    Reduced,

    /** A predictive-back scrub, shifted toward [NavMotion.swipeEdge]. */
    Predictive,

    /** TabletCrossfade predictive back: the upper page shifts toward the end, shrinks and fades; the lower one fades in late from the start side. */
    PredictiveShrink,
}

/**
 * One navigation move, resolved from [MotionTokens]: what the shell's scrim and container
 * transform read while it runs, and the [transform] NavDisplay plays.
 *
 * NavDisplay decides the stacking: the incoming scene is drawn above on a push and below on a
 * pop or predictive back ([incomingOnTop]). Every transform therefore fades or moves only the
 * UPPER page and holds the lower one opaque: two pages never fade at once (the tablet blink).
 */
internal data class NavMotion(
    val kind: NavMotionKind,
    val pop: Boolean,
    val durationMillis: Int,
    val easing: Easing,
    val enterDelayMillis: Int = 0,
    val exitMillis: Int = durationMillis,
    val parallaxFraction: Float = 0f,
    val scrimAlpha: Float = 0f,
    val swipeEdge: Int = NavigationEvent.EDGE_NONE,
) {
    val incomingOnTop: Boolean get() = !pop

    fun transform(layoutDirection: LayoutDirection, density: Density): ContentTransform {
        val endward = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
        val travel = tween<IntOffset>(durationMillis, easing = easing)
        val fade = tween<Float>(durationMillis, easing = easing)
        // Keeps a page opaque. A held page stays composed because every scene also runs the
        // scene canvas' scrim clock for the move's full length (NavSceneCanvas.kt).
        val hold = tween<Float>(durationMillis)
        val parallax = { width: Int -> (width * parallaxFraction).roundToInt() }
        return when (kind) {
            NavMotionKind.Slide -> if (pop) {
                slideInHorizontally(travel) { -endward * parallax(it) } togetherWith
                    slideOutHorizontally(travel) { endward * it }
            } else {
                slideInHorizontally(travel) { endward * it } togetherWith
                    slideOutHorizontally(travel) { -endward * parallax(it) }
            }
            NavMotionKind.Axis -> {
                val from = if (pop) -endward else endward
                slideInHorizontally(travel) { from * it } togetherWith slideOutHorizontally(travel) { -from * it }
            }
            NavMotionKind.Lift -> fadeIn(hold, initialAlpha = 1f) togetherWith fadeOut(hold, targetAlpha = 1f)
            NavMotionKind.Rise -> {
                val risePx = with(density) { MotionTokens.MixTabRise.roundToPx() }
                val rise = slideInVertically(travel) { risePx }
                if (pop) {
                    (fadeIn(hold, initialAlpha = 1f) + rise) togetherWith fadeOut(fade)
                } else {
                    (fadeIn(fade) + rise) togetherWith fadeOut(hold, targetAlpha = 1f)
                }
            }
            NavMotionKind.FadeThrough ->
                fadeIn(tween(durationMillis, enterDelayMillis, easing)) togetherWith
                    fadeOut(tween(exitMillis, easing = MotionTokens.EasingLinear))
            NavMotionKind.Reduced, NavMotionKind.Crossfade -> if (pop) {
                fadeIn(hold, initialAlpha = 1f) togetherWith fadeOut(fade)
            } else {
                fadeIn(fade) togetherWith fadeOut(hold, targetAlpha = 1f)
            }
            NavMotionKind.Predictive -> {
                // A swipe from the right edge pulls the screen left; from the left (or no edge), right.
                val toward = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
                val shift = slideOutHorizontally(travel) { toward * (it * PredictiveShiftFraction).roundToInt() }
                val lateFade = fadeOut(
                    tween(PredictiveFadeOutMillis, durationMillis - PredictiveFadeOutMillis, easing),
                )
                fadeIn(hold, initialAlpha = 1f) togetherWith (shift + lateFade)
            }
            NavMotionKind.PredictiveShrink -> {
                // The reference tablet back: the outgoing page travels toward the end (right in LTR) whichever edge the
                // swipe starts from, shrinking a little and fading out; the page below fades in late from a small start-side offset.
                val outFade = tween<Float>((durationMillis * PredictiveTabletFadeOutFraction).roundToInt(), easing = easing)
                val inFade = tween<Float>(durationMillis / 2, delayMillis = durationMillis / 2, easing = easing)
                val outgoing = slideOutHorizontally(travel) { endward * (it * PredictiveTabletShiftFraction).roundToInt() } +
                    scaleOut(fade, PredictiveTabletScale) + fadeOut(outFade)
                val incoming = slideInHorizontally(travel) { -endward * (it * PredictiveTabletIncomingOffset).roundToInt() } +
                    fadeIn(inFade, initialAlpha = 0f)
                incoming togetherWith outgoing
            }
        }
    }

    companion object {
        /** Predictive back scrubs this timeline linearly with the gesture; NavDisplay settles the
         *  remainder from the live value on commit or cancel. */
        val PredictiveMillis = MotionTokens.NavFullSlidePop

        /** The outgoing screen's shift toward the gesture, as a fraction of the width. */
        const val PredictiveShiftFraction = 0.08f

        /** TabletCrossfade predictive back: how far the outgoing page travels toward the end, as a fraction of the width. */
        const val PredictiveTabletShiftFraction = 0.45f

        /** The outgoing page's scale at the end of the scrub. */
        const val PredictiveTabletScale = 0.95f

        /** The outgoing page is gone after this share of the scrub. */
        const val PredictiveTabletFadeOutFraction = 0.85f

        /** The page below fades in over the scrub's second half from this start-side offset, as a fraction of the width. */
        const val PredictiveTabletIncomingOffset = 0.04f

        /** The outgoing screen fades out over the last stretch of the timeline. */
        val PredictiveFadeOutMillis = MotionTokens.DurationMedium / 2

        /** Before the first move: nothing scrims, nothing lifts. */
        val Idle = NavMotion(NavMotionKind.Reduced, pop = false, durationMillis = MotionTokens.DurationNone, easing = MotionTokens.EasingLinear)
    }
}

/**
 * The shell's one chooser over NavDisplay scene metadata ([OWNER_TAB], [ENTRY_KEY]); [from] is
 * the outgoing entry's, [to] the incoming one's. [liftable] says the game has a card to grow
 * out of (push) or shrink back into (pop); only the tablet form reads it.
 * - Reduced motion: the upper page fades for every case.
 * - Another owner tab, or the layer above the tabs opening or closing: phone fade-through;
 *   tablet rise-over, or with [tabletCrossfade] an in-place crossfade of [MotionTokens.TabletTabCrossfade] (the Profile/Settings layer opening or closing takes [MotionTokens.TabletPageCrossfade]).
 * - Phone, same tab: the full-screen slide, result included.
 * - Tablet, into or out of a game: lift from its card, or the phone slide without one
 *   (deep link, history play-again). Game ⇄ result and every other move: full-width shared axis, or with
 *   [tabletCrossfade] an in-place crossfade of [MotionTokens.TabletPageCrossfade].
 */
internal fun navMotion(
    from: Map<String, Any>,
    to: Map<String, Any>,
    pop: Boolean,
    reduceMotion: Boolean,
    form: NavForm,
    liftable: Boolean = false,
    tabletCrossfade: Boolean = false,
): NavMotion {
    val opensGame = !pop && to[ENTRY_KEY].isBoardEntry() && !from[ENTRY_KEY].isResultEntry()
    val closesGame = pop && from[ENTRY_KEY].isBoardEntry()
    return when {
        reduceMotion -> reduced(pop, MotionTokens.ReducedFadeDuration)
        from[OWNER_TAB] != to[OWNER_TAB] -> when {
            form == NavForm.Phone -> tabFadeThrough(pop)
            // The ticket calls opening or closing the Profile/Settings layer a page move, not a tab switch.
            tabletCrossfade -> crossfade(
                pop,
                if (from[OWNER_TAB] == LAYER_OWNER || to[OWNER_TAB] == LAYER_OWNER) MotionTokens.TabletPageCrossfade else MotionTokens.TabletTabCrossfade,
            )
            else -> rise(pop)
        }
        form == NavForm.Phone -> slide(pop)
        opensGame || closesGame -> if (liftable) lift(pop) else slide(pop)
        tabletCrossfade -> crossfade(pop, MotionTokens.TabletPageCrossfade)
        else -> axis(pop)
    }
}

/**
 * Predictive back: a gesture-scrubbed preview of the real destination, the same for every
 * back. The outgoing screen shifts with the swipe ([swipeEdge], a [NavigationEvent] edge) over
 * the opaque destination; under reduced motion the scrub only fades the outgoing screen. With [shrink]
 * (TabletCrossfade on a tablet) the screen always travels toward the end and fades, shrinking a little, whichever edge.
 */
internal fun predictivePopMotion(swipeEdge: Int, reduceMotion: Boolean, shrink: Boolean = false): NavMotion =
    if (reduceMotion) {
        reduced(pop = true, NavMotion.PredictiveMillis).copy(easing = MotionTokens.EasingLinear)
    } else {
        NavMotion(
            if (shrink) NavMotionKind.PredictiveShrink else NavMotionKind.Predictive,
            pop = true,
            durationMillis = NavMotion.PredictiveMillis,
            easing = MotionTokens.EasingLinear,
            swipeEdge = swipeEdge,
        )
    }

private fun slide(pop: Boolean) = NavMotion(
    NavMotionKind.Slide,
    pop = pop,
    durationMillis = if (pop) MotionTokens.NavFullSlidePop else MotionTokens.NavFullSlideEnter,
    easing = if (pop) MotionTokens.EasingStandard else MotionTokens.EasingEmphasizedDecelerate,
    parallaxFraction = MotionTokens.NavParallaxFraction,
    scrimAlpha = MotionTokens.NavScrimAlpha,
)

private fun lift(pop: Boolean) = NavMotion(
    NavMotionKind.Lift,
    pop = pop,
    durationMillis = if (pop) MotionTokens.LiftExit else MotionTokens.LiftEnter,
    easing = MotionTokens.EasingStandard,
    scrimAlpha = MotionTokens.LiftScrimAlpha,
)

private fun axis(pop: Boolean) = NavMotion(
    NavMotionKind.Axis,
    pop = pop,
    durationMillis = if (pop) MotionTokens.MixAxisPop else MotionTokens.MixAxisPush,
    easing = MotionTokens.EasingStandard,
)

private fun crossfade(pop: Boolean, millis: Int) = NavMotion(
    NavMotionKind.Crossfade,
    pop = pop,
    durationMillis = millis,
    easing = MotionTokens.EasingStandard,
)

private fun rise(pop: Boolean) = NavMotion(
    NavMotionKind.Rise,
    pop = pop,
    durationMillis = MotionTokens.MixTabIn,
    easing = MotionTokens.EasingStandard,
)

private fun tabFadeThrough(pop: Boolean) = NavMotion(
    NavMotionKind.FadeThrough,
    pop = pop,
    durationMillis = MotionTokens.NavTabEnter,
    easing = MotionTokens.EasingStandard,
    enterDelayMillis = MotionTokens.NavTabEnterDelay,
    exitMillis = MotionTokens.NavTabExit,
)

private fun reduced(pop: Boolean, millis: Int) = NavMotion(
    NavMotionKind.Reduced,
    pop = pop,
    durationMillis = millis,
    easing = MotionTokens.EasingStandard,
)
