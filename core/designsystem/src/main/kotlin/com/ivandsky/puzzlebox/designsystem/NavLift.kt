package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import kotlin.math.roundToInt

/**
 * The tablet container transform (polish 04, variant D): a game screen grows out of the card
 * that opened it and shrinks back into it. The shell owns one per window and decides per move
 * whether this one [lifting]s; cards mark themselves with [navLiftSource], the game screen with
 * [navLiftTarget], both keyed by the game's nav key. Off the tablet form (no [LocalNavLift]),
 * in previews and in tests both modifiers are no-ops.
 */
class NavLift(val sharedScope: SharedTransitionScope) {

    /** Whether the move in flight is a lift; every other move leaves the matched pair alone. */
    var lifting: Boolean by mutableStateOf(false)

    /** The lift in flight's duration: [MotionTokens.LiftEnter] out, [MotionTokens.LiftExit] back. */
    var liftMillis: Int by mutableIntStateOf(MotionTokens.LiftEnter)

    // Ref-counted: the same game can show as more than one card (a row and a featured slot).
    private val onScreen = mutableStateMapOf<Any, Int>()

    /** A card keyed [key] is composed right now, so a push of [key] has something to grow out of. */
    fun isOnScreen(key: Any?): Boolean = key != null && (onScreen[key] ?: 0) > 0

    internal fun register(key: Any) {
        onScreen[key] = (onScreen[key] ?: 0) + 1
    }

    internal fun unregister(key: Any) {
        val left = (onScreen[key] ?: 0) - 1
        if (left > 0) onScreen[key] = left else onScreen.remove(key)
    }

    internal val config = object : SharedTransitionScope.SharedContentConfig {
        override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
            get() = lifting
    }

    internal val boundsTransform = BoundsTransform { _, _ ->
        tween(liftMillis, easing = MotionTokens.EasingStandard)
    }
}

/** The window's [NavLift], provided by the shell on the tablet form only. */
val LocalNavLift = staticCompositionLocalOf<NavLift?> { null }

/** The nav scene a [navLiftSource] or [navLiftTarget] sits in; the shell provides each scene's. */
val LocalNavLiftVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a card the game keyed [key] lifts out of. The card's own content fades off the
 * growing container over its first [MotionTokens.MixLiftCardFade], and back on late in the
 * return, so the game never reads as a stretched card.
 */
fun Modifier.navLiftSource(key: Any): Modifier = composed {
    val lift = LocalNavLift.current
    val visibility = LocalNavLiftVisibility.current
    if (lift == null || visibility == null) return@composed this
    DisposableEffect(lift, key) {
        lift.register(key)
        onDispose { lift.unregister(key) }
    }
    val fadeOutMillis = (MotionTokens.LiftEnter * MotionTokens.MixLiftCardFade).roundToInt()
    val fadeInMillis = (MotionTokens.LiftExit * MotionTokens.MixLiftCardFade).roundToInt()
    val fadeInDelay = (MotionTokens.LiftExit * CARD_RETURN_START).roundToInt()
    with(lift.sharedScope) {
        this@composed.sharedBounds(
            sharedContentState = rememberSharedContentState(key, lift.config),
            animatedVisibilityScope = visibility,
            enter = fadeIn(tween(fadeInMillis, fadeInDelay, MotionTokens.EasingStandard)),
            exit = fadeOut(tween(fadeOutMillis, easing = MotionTokens.EasingStandard)),
            boundsTransform = lift.boundsTransform,
            zIndexInOverlay = 1f,
        )
    }
}

/**
 * Marks the game screen keyed [key] that grows out of its [navLiftSource] card: scaled to
 * the animating bounds and clipped to [shape] while in flight, opaque throughout.
 */
fun Modifier.navLiftTarget(key: Any, shape: Shape): Modifier = composed {
    val lift = LocalNavLift.current
    val visibility = LocalNavLiftVisibility.current
    if (lift == null || visibility == null) return@composed this
    with(lift.sharedScope) {
        this@composed.sharedBounds(
            sharedContentState = rememberSharedContentState(key, lift.config),
            animatedVisibilityScope = visibility,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = lift.boundsTransform,
            resizeMode = scaleToBounds(ContentScale.Crop, Alignment.Center),
            clipInOverlayDuringTransition = OverlayClip(shape),
            zIndexInOverlay = 0f,
        )
    }
}

// The returning card's content starts fading back once a quarter of the shrink is done.
private const val CARD_RETURN_START = 0.25f
