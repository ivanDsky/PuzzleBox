package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.EdgePull
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.swipeStep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The recap story as a stack of cards: the page on top over the next [RecapMotion.DeckStackVisible] - 1 peeking out
 * below it, each [RecapMotion.DeckStackStep] lower and [RecapMotion.DeckStackScaleStep] smaller. A tap on the left third
 * steps back and anywhere else steps on, and nothing moves by itself (A7). Stepping on throws the top card off to the left
 * while the next is promoted; stepping back brings the thrown card back over the stack. A drag follows the finger with a
 * tilt and rubber-bands past the first or last page; a hold slides the top card down to show the next. Reduced motion
 * swaps cards with a fade and does not follow the finger.
 *
 * [yearFlick] tells which positions throw like a month of a year (longer, steeper, and the next card springs up); [face] draws a card inside its frame and [palette] colours it, both by [RecapDeckState.position] (the cover is 0). A face starts afresh when it comes up from below and when
 * [RecapDeckState.entrance] bumps, so its entrance plays then, and never restarts while it is thrown away.
 */
@Composable
internal fun RecapDeck(
    deck: RecapDeckState,
    palette: (position: Int) -> PaletteEntry,
    modifier: Modifier = Modifier,
    yearFlick: (position: Int) -> Boolean = { false },
    face: @Composable (position: Int) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val top = deck.position
    val holding = deck.holding
    val stack = remember { Animatable(top.toFloat()) }
    val thrown = remember { Animatable(top.toFloat()) }
    val peek = remember { Animatable(if (holding) 1f else 0f) }
    val drag = remember(scope) { DeckDrag(scope) }
    val deal = rememberDeal(deck, reduced)
    val dealFace = remember(deck) { top }
    var throwFrom by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }

    LaunchedEffect(top, reduced) {
        val from = stack.targetValue.roundToInt()
        if (from == top) return@LaunchedEffect
        val forward = top > from
        // A swipe hands the card over where the finger left it, so the throw carries on from there.
        val flick = forward && yearFlick(from)
        throwFrom = if (forward) (drag.offset / (throwTravel(flick) * width)).coerceIn(0f, 1f) else 0f
        if (forward) {
            thrown.snapTo(from + throwFrom)
            drag.drop()
        }
        if (reduced) {
            stack.snapTo(top.toFloat())
            thrown.animateTo(top.toFloat(), tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear))
            return@LaunchedEffect
        }
        val settle = tween<Float>(if (forward) RecapMotion.DeckStackPromote else RecapMotion.ThrowBackDuration, easing = MotionTokens.EasingStandard)
        val promote = if (flick) spring<Float>(RecapMotion.YearPromoteDamping, RecapMotion.YearPromoteStiffness) else settle
        val duration = if (flick) RecapMotion.YearThrowDuration else RecapMotion.ThrowDuration
        launch { stack.animateTo(top.toFloat(), promote) }
        thrown.animateTo(top.toFloat(), if (forward) tween(duration, easing = MotionTokens.EasingEmphasizedAccelerate) else settle)
    }
    LaunchedEffect(holding, reduced) {
        val duration = when {
            reduced -> MotionTokens.ReducedFadeDuration
            holding -> RecapMotion.DeckStackHoldIn
            else -> RecapMotion.DeckStackHoldOut
        }
        peek.animateTo(if (holding) 1f else 0f, tween(duration, easing = if (reduced) MotionTokens.EasingLinear else MotionTokens.EasingStandard))
    }

    // The thrown card stays until it has flown; a card leaving the stack stays until it has faded.
    val lowest by remember { derivedStateOf { floor(thrown.value).toInt() } }
    val deepest by remember { derivedStateOf { ceil(stack.value).toInt() } }
    val first = min(top, lowest)
    val last = min(max(top, deepest) + RecapMotion.DeckStackVisible - 1, deck.stackSize - 1)
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .pointerInput(deck, reduced) {
                detectDeckGestures(
                    onTap = { at -> if (at.x < size.width * BackZone) deck.back() else deck.forward() },
                    onHold = deck::hold,
                    onDrag = { dx -> if (!reduced) drag.by(dx, deck) },
                    onDragEnd = { dragPx, velocityPx ->
                        val widthPx = size.width.toFloat()
                        val commit = min(widthPx * SwipeMotion.CommitFraction, SwipeMotion.CommitCap.toPx())
                        val step = swipeStep(dragPx, velocityPx, widthPx, SwipeMotion.FlingVelocity.toPx(), commit)
                        val threw = step == 1 && deck.forward()
                        if (step == -1) deck.back()
                        if (!threw) drag.settle()
                    },
                )
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next card") { deck.forward(); true },
                    CustomAccessibilityAction("Previous card") { deck.back(); true },
                )
            }
            .testTag("recap.pager"),
    ) {
        // Lowest page last, so the top card and a thrown one draw over the stack.
        for (index in last downTo first) key(index) {
            val entry = palette(index)
            val onTop = index == top
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val behind = (index - stack.value).coerceAtLeast(0f)
                        val flown = (thrown.value - index).coerceIn(0f, 1f)
                        val fade = if (flown >= 1f) 0f else ((1f - flown) / (1f - throwFrom)).coerceIn(0f, 1f)
                        var scale = 1f - behind * RecapMotion.DeckStackScaleStep
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = behind * RecapMotion.DeckStackStep.toPx()
                        alpha = (RecapMotion.DeckStackVisible - behind).coerceIn(0f, 1f) * fade
                        if (!reduced) {
                            val flick = yearFlick(index)
                            translationX = flown * throwTravel(flick) * size.width
                            rotationZ = flown * (if (flick) RecapMotion.YearThrowTilt else RecapMotion.ThrowTilt)
                        }
                        if (index == drag.face) {
                            translationX += drag.shift
                            rotationZ += drag.shift / size.width * RecapMotion.DragTilt
                        }
                        if (onTop) {
                            // Reduced motion crossfades to the peek instead of sliding: out where it was, in where it peeks.
                            val held = when {
                                !reduced -> peek.value
                                peek.value >= 0.5f -> 1f
                                else -> 0f
                            }
                            if (reduced) alpha *= abs(2f * peek.value - 1f)
                            translationY += held * RecapMotion.HoldPeek * size.height
                            scale *= lerp(1f, MotionTokens.PressScale, held)
                        }
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(if (index == dealFace) Modifier.dealt(deal, shape) else Modifier)
                    .clip(shape)
                    .background(entry.field)
                    .then(if (onTop) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                if (index != dealFace || !deal.faceDown) {
                    Box(Modifier.fillMaxSize().padding(CardRim).clip(MaterialTheme.shapes.large).background(entry.plate)) {
                        key(index <= top, deck.entrance) { face(index) }
                    }
                }
            }
        }
    }
}

private fun throwTravel(yearFlick: Boolean): Float = if (yearFlick) RecapMotion.YearThrowTravel else RecapMotion.ThrowTravel

/** The finger's pull on the top card, px: followed while dragging, rubber-banded past the ends, settled when let go. */
private class DeckDrag(private val scope: CoroutineScope) {
    private val edge = EdgePull(scope)
    private var settling: Job? = null

    var offset by mutableFloatStateOf(0f)
        private set

    /** The page the pull moves; it stays on that page while it settles, even after the deck has stepped. */
    var face by mutableIntStateOf(0)
        private set

    /** How far the card is drawn shifted: the pull plus the rubber band past the first or last page. */
    val shift: Float get() = offset + edge.lean

    fun by(dx: Float, deck: RecapDeckState) {
        settling?.cancel()
        face = deck.position
        val low = if (deck.position < deck.stackSize - 1) Float.NEGATIVE_INFINITY else 0f
        val high = if (deck.canStepBack) Float.POSITIVE_INFINITY else 0f
        edge.dragBy(dx) { rest ->
            val next = (offset + rest).coerceIn(low, high)
            val taken = next - offset
            offset = next
            rest - taken
        }
    }

    fun settle() {
        edge.release()
        settling = scope.launch { animate(offset, 0f, animationSpec = SwipeMotion.Settle) { value, _ -> offset = value } }
    }

    /** The deck threw the card on, and the throw takes over from where the finger left it. */
    fun drop() {
        settling?.cancel()
        offset = 0f
        edge.release()
    }
}

/** The left share of the deck a tap steps back from; the rest steps on. */
private const val BackZone = 1f / 3f

/** The band of the card's field colour around its plate. */
private val CardRim = 8.dp
