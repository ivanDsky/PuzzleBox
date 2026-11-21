package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * The container transform (polish 04, variant D): a screen grows out of the card that opened it
 * and shrinks back into it. The shell owns one per window and decides per move whether this one
 * [lifting]s: a game on the tablet form, a recap story on both. Cards mark themselves with
 * [navLiftSource], the game screen with [navLiftTarget] and the story with [navLiftReveal], all
 * keyed by the screen's nav key. Without [LocalNavLift] (previews, tests) every modifier here is a no-op.
 */
class NavLift(val sharedScope: SharedTransitionScope) {

    /** Whether the move in flight is a lift; every other move leaves the matched pair alone. */
    var lifting: Boolean by mutableStateOf(false)

    /** The lift in flight's duration: [MotionTokens.LiftEnter] out, [MotionTokens.LiftExit] back. */
    var liftMillis: Int by mutableIntStateOf(MotionTokens.LiftEnter)

    /** The screen that lifted in last, written by the shell: only it shrinks back into its card on its pop. */
    var key: Any? = null

    // Ref-counted: the same game can show as more than one card (a row and a featured slot).
    private val onScreen = mutableStateMapOf<Any, Int>()

    // Each card's own size, px, which [navLiftReveal] rounds its corners against; read while drawing, never observed.
    internal val sourceSizes = mutableMapOf<Any, IntSize>()

    /** A card keyed [key] is composed right now, so a push of [key] has something to grow out of. */
    fun isOnScreen(key: Any?): Boolean = key != null && (onScreen[key] ?: 0) > 0

    /** Whether [scene] is coming in by the lift in flight: a screen growing out of its card, or the page a screen shrinks back into. */
    fun isLiftingIn(scene: AnimatedVisibilityScope): Boolean =
        lifting && scene.transition.targetState == EnterExitState.Visible && scene.transition.currentState != EnterExitState.Visible

    internal fun register(key: Any) {
        onScreen[key] = (onScreen[key] ?: 0) + 1
    }

    internal fun unregister(key: Any) {
        val left = (onScreen[key] ?: 0) - 1
        if (left > 0) {
            onScreen[key] = left
        } else {
            onScreen.remove(key)
            sourceSizes.remove(key)
        }
    }

    internal val config = object : SharedTransitionScope.SharedContentConfig {
        override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
            get() = lifting
    }

    internal val boundsTransform = BoundsTransform { _, _ ->
        tween(liftMillis, easing = MotionTokens.EasingStandard)
    }
}

/** The window's [NavLift], provided by the shell on both forms. */
val LocalNavLift = staticCompositionLocalOf<NavLift?> { null }

/** The nav scene a [navLiftSource] or [navLiftTarget] sits in; the shell provides each scene's. */
val LocalNavLiftVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a card the screen keyed [key] lifts out of. The card's own content fades off the
 * growing container over its first [MotionTokens.MixLiftCardFade], and back on late in the
 * return, so the screen never reads as a stretched card. With [scaleContent] false the content
 * keeps its own size while it fades, so its text is never scaled.
 */
fun Modifier.navLiftSource(key: Any, scaleContent: Boolean = true): Modifier = composed {
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
            resizeMode = scaleToBounds(if (scaleContent) ContentScale.FillWidth else ContentScale.None, Alignment.Center),
            zIndexInOverlay = 1f,
        ).onSizeChanged { lift.sourceSizes[key] = it }
    }
}

/**
 * Marks the game screen keyed [key] that grows out of its [navLiftSource] card: scaled to
 * the animating bounds and clipped to [shape] while in flight, opaque throughout. Its card gone
 * by its pop, it fades out instead ([rememberCardlessFade]).
 */
fun Modifier.navLiftTarget(key: Any, shape: Shape): Modifier = composed {
    val lift = LocalNavLift.current
    val visibility = LocalNavLiftVisibility.current
    if (lift == null || visibility == null) return@composed this
    val cardless = rememberCardlessFade(lift, visibility, key)
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
        ).then(cardless)
    }
}

/**
 * Marks the screen keyed [key] that its [navLiftSource] card reveals rather than stretches: the
 * screen keeps its own size while the animating bounds clip it, opaque throughout, and their
 * corners go from the card's [cardShape] at the card's size to square at the screen's. Text is
 * never scaled. Its card gone by its pop, it fades out instead ([rememberCardlessFade]).
 */
fun Modifier.navLiftReveal(key: Any, cardShape: CornerBasedShape): Modifier = composed {
    val lift = LocalNavLift.current
    val visibility = LocalNavLiftVisibility.current
    if (lift == null || visibility == null) return@composed this
    val clip = remember(lift, key, cardShape) { CornerMorphClip(lift, key, cardShape) }
    val cardless = rememberCardlessFade(lift, visibility, key)
    with(lift.sharedScope) {
        this@composed.sharedBounds(
            sharedContentState = rememberSharedContentState(key, lift.config),
            animatedVisibilityScope = visibility,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = lift.boundsTransform,
            resizeMode = scaleToBounds(ContentScale.None, Alignment.Center),
            clipInOverlayDuringTransition = clip,
            zIndexInOverlay = 0f,
        ).onSizeChanged { clip.screen = it }.then(cardless)
    }
}

/**
 * The screen keyed [key] shrinking back by the lift with no card keyed [key] on the page below
 * (scrolled off, or the list changed while it was open) fades out over [MotionTokens.ReducedFadeDuration].
 * The lift holds both pages opaque without animating them, so with nothing to shrink into the screen
 * would cut; this fade runs on the scene's own transition, so it holds the screen until it is done.
 * Whether the card is there is read as it draws: the shell picks the lift before the page below composes.
 */
@Composable
private fun rememberCardlessFade(lift: NavLift, scene: AnimatedVisibilityScope, key: Any): Modifier {
    val fade = scene.transition.animateFloat(
        transitionSpec = { tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingStandard) },
        label = "cardless fade",
    ) { state -> if (state == EnterExitState.PostExit && lift.lifting) 0f else 1f }
    return Modifier.graphicsLayer { alpha = if (lift.key == key && !lift.isOnScreen(key)) fade.value else 1f }
}

/**
 * Pulses the block once ([timePulse]) after the screen keyed [key] has shrunk back into the
 * card it sits on. A crossfade back does not pulse it, nor does reduced motion.
 */
fun Modifier.navLiftPulse(key: Any): Modifier = composed {
    val lift = LocalNavLift.current
    val scene = LocalNavLiftVisibility.current
    // Read once, as the card comes back: the shrink is under way only while its page is still coming in.
    val returning = remember { lift != null && scene != null && lift.key == key && lift.isLiftingIn(scene) }
    var landed by remember { mutableStateOf(false) }
    if (returning && scene != null) {
        LaunchedEffect(scene) {
            snapshotFlow { scene.transition.currentState }.first { it == EnterExitState.Visible }
            landed = true
        }
    }
    timePulse(landed)
}

/** Rounds the animating bounds by how far they have come from the card's size toward the screen's. */
private class CornerMorphClip(
    private val lift: NavLift,
    private val key: Any,
    private val cardShape: CornerBasedShape,
) : SharedTransitionScope.OverlayClip {
    var screen = IntSize.Zero
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val card = lift.sourceSizes[key] ?: screen
        val span = (screen.width - card.width) + (screen.height - card.height)
        val grown = if (span <= 0) 1f else (bounds.width - card.width + bounds.height - card.height) / span
        val radius = cardShape.topStart.toPx(card.toSize(), density) * (1f - grown.coerceIn(0f, 1f))
        path.reset()
        path.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
        return path
    }
}

// The returning card's content starts fading back once a quarter of the shrink is done.
private const val CARD_RETURN_START = 0.25f
