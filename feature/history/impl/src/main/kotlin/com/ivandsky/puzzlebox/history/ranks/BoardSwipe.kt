package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A drag this far across the board (a quarter of its width) commits the swipe. */
internal const val SWIPE_COMMIT_FRACTION = 0.25f

/** The board follows the finger this much of the way: a hint of the swipe, not a page slide. */
private const val FOLLOW = 0.2f

private val FLING_VELOCITY = 700.dp

/**
 * How many boards a horizontal drag of [dragPx] released at [velocityPx] (both px, negative = leftwards)
 * walks: one forward for a leftward swipe, one back for a rightward one, none when it is neither a
 * fling ([flingPx]/s) nor [SWIPE_COMMIT_FRACTION] of [widthPx]. A fling decides by its own direction.
 */
internal fun swipeStep(dragPx: Float, velocityPx: Float, widthPx: Float, flingPx: Float): Int = when {
    abs(velocityPx) >= flingPx -> -sign(velocityPx).toInt()
    abs(dragPx) >= widthPx * SWIPE_COMMIT_FRACTION -> -sign(dragPx).toInt()
    else -> 0
}

/**
 * The axis a drag that has travelled [dx], [dy] px from its start is locked to: none inside the touch
 * [slop], then the one it has moved further along. Horizontal is the board's swipe; vertical (a tie
 * too) is the page's scroll and its pull-to-refresh.
 */
internal fun swipeAxis(dx: Float, dy: Float, slop: Float): Orientation? = when {
    max(abs(dx), abs(dy)) < slop -> null
    abs(dx) > abs(dy) -> Orientation.Horizontal
    else -> Orientation.Vertical
}

/**
 * One finger's drag, locked to its dominant axis at the touch slop ([swipeAxis]). Horizontal: every
 * event from then until the finger lifts is consumed, so the page's scroll and its pull-to-refresh
 * can neither start nor take the drag over; [onDrag] hears each horizontal delta and [onEnd] the
 * whole horizontal travel and the release velocity (px, px/s), or no travel at all when the system
 * takes the touch away mid-swipe. Vertical, or a drag something else consumed first: the gesture is
 * left alone to its end.
 */
private suspend fun PointerInputScope.detectSwipe(onDrag: (Float) -> Unit, onEnd: (dragPx: Float, velocityPx: Float) -> Unit) =
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocity = VelocityTracker().apply { addPointerInputChange(down) }
        var travel = Offset.Zero
        var dragged = 0f
        var swiping = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                velocity.addPointerInputChange(change)
                if (!swiping) {
                    if (change.isConsumed) return@awaitEachGesture
                    travel += change.positionChange()
                    when (swipeAxis(travel.x, travel.y, viewConfiguration.touchSlop)) {
                        Orientation.Horizontal -> swiping = true
                        Orientation.Vertical -> return@awaitEachGesture
                        null -> {
                            // A parent sees the event after this one: ask again once everyone has.
                            awaitPointerEvent(PointerEventPass.Final)
                            if (change.isConsumed) return@awaitEachGesture
                        }
                    }
                }
                if (swiping) {
                    val dx = change.positionChange().x
                    dragged += dx
                    onDrag(dx)
                    event.changes.forEach { it.consume() }
                }
            }
        } catch (cancelled: CancellationException) {
            // A cancelled touch (a palm, a system gesture) is no swipe, but the board must not stay leant.
            if (swiping) onEnd(0f, 0f)
            throw cancelled
        }
        if (swiping) onEnd(dragged, velocity.calculateVelocity().x)
    }

/**
 * Horizontal swipe on [content] that reports one step per committed swipe through [onStep] (+1 next,
 * -1 previous). A drag belongs to one axis from its touch slop on ([swipeAxis]): a mostly-sideways one
 * is the swipe and never scrolls the page or pulls its refresh, a mostly-vertical one is the page's
 * and never moves the board. The content leans a little with the finger and settles back, and a new
 * drag picks it up where it is; the new board is the parent's re-ranking of the same list, not a
 * slide. Assistive tech gets both steps as actions.
 */
@Composable
internal fun BoardSwipe(onStep: (Int) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var lean by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    var width by remember { mutableFloatStateOf(0f) }
    val fling = with(LocalDensity.current) { FLING_VELOCITY.toPx() }
    val still = rememberReducedMotion()
    val step by rememberUpdatedState(onStep)
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next board") { onStep(1); true },
                    CustomAccessibilityAction("Previous board") { onStep(-1); true },
                )
            }
            .pointerInput(fling) {
                detectSwipe(
                    onDrag = { dx ->
                        settling?.cancel()
                        lean += dx * FOLLOW
                    },
                    onEnd = { dragPx, velocityPx ->
                        val by = swipeStep(dragPx, velocityPx, width, fling)
                        if (by != 0) step(by)
                        settling = scope.launch {
                            animate(lean, 0f, animationSpec = spring(MotionTokens.SpringDampingDefault, Spring.StiffnessMediumLow)) { value, _ -> lean = value }
                        }
                    },
                )
            }
            .graphicsLayer { translationX = if (still) 0f else lean },
    ) { content() }
}
