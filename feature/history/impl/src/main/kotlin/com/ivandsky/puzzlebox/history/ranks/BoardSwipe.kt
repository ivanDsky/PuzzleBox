package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.animate
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
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How many boards a horizontal drag of [dragPx] released at [velocityPx] (both px, negative = leftwards)
 * walks: one forward for a leftward swipe, one back for a rightward one, none when it is neither a
 * fling ([flingPx]/s) nor a drag of [commitPx] ([SwipeMotion.CommitFraction] of [widthPx] unless the
 * caller caps it). A fling decides by its own direction.
 */
internal fun swipeStep(
    dragPx: Float,
    velocityPx: Float,
    widthPx: Float,
    flingPx: Float,
    commitPx: Float = widthPx * SwipeMotion.CommitFraction,
): Int = when {
    abs(velocityPx) >= flingPx -> -sign(velocityPx).toInt()
    abs(dragPx) >= commitPx -> -sign(dragPx).toInt()
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
 * Horizontal swipe on [content] between the hub's [count] boards, of which it is on the one at [position]. The
 * finger scrubs [scrub] towards the neighbouring board as it goes, a board width for the whole way, and whatever
 * draws from the scrub (the list re-ranking in place, the world behind it) follows it there and back. On release
 * the scrub settles on the board the swipe picked and [onStep] hears how many boards that is from [position]
 * (+1 next, -1 previous); a new drag picks the scrub up wherever it is. Until then the finger moves the scrub 1:1.
 * A swipe commits at a light flick or at a quarter of the width, and never needs more than
 * [SwipeMotion.CommitCap]; every value is a [SwipeMotion] token.
 *
 * A drag belongs to one axis from its touch slop on ([swipeAxis]): a mostly-sideways one is the swipe and never
 * scrolls the page or pulls its refresh, a mostly-vertical one is the page's and never moves the board. Nothing
 * slides: past the first or last board the content gives a little with the finger and settles back. Reduced
 * motion leaves the scrub alone and steps on release. Assistive tech gets both steps as actions.
 */
@Composable
internal fun BoardSwipe(
    scrub: BoardScrub,
    position: Int,
    count: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var over by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    var width by remember { mutableFloatStateOf(0f) }
    val fling = with(LocalDensity.current) { SwipeMotion.FlingVelocity.toPx() }
    val commitCap = with(LocalDensity.current) { SwipeMotion.CommitCap.toPx() }
    val still = rememberReducedMotion()
    val step by rememberUpdatedState(onStep)
    val at by rememberUpdatedState(position)
    val boards by rememberUpdatedState(count)
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next board") { onStep(1); true },
                    CustomAccessibilityAction("Previous board") { onStep(-1); true },
                )
            }
            .pointerInput(fling, commitCap, still, scrub) {
                detectSwipe(
                    onDrag = { dx ->
                        if (!still && width > 0f) {
                            settling?.cancel()
                            // A pull past the end is taken back first; only what is left of the move scrubs.
                            val back = if (over * dx < 0f) dx.coerceIn(-abs(over), abs(over)) else 0f
                            over += back + scrub.dragBy((dx - back) / width, boards) * width
                        }
                    },
                    onEnd = { dragPx, velocityPx ->
                        val commit = min(width * SwipeMotion.CommitFraction, commitCap)
                        if (still || width <= 0f) {
                            val by = swipeStep(dragPx, velocityPx, width, fling, commit)
                            if (by != 0) step(by)
                        } else {
                            if (dragPx == 0f && velocityPx == 0f) {
                                scrub.cancel()
                            } else {
                                val target = scrub.release(at, -velocityPx / width, fling / width, commit / width)
                                if (target != at) step(target - at)
                            }
                            settling = scope.launch {
                                animate(over, 0f, animationSpec = SwipeMotion.Settle) { value, _ -> over = value }
                            }
                        }
                    },
                )
            }
            .graphicsLayer { translationX = if (still) 0f else over * SwipeMotion.EdgeFollow },
    ) { content() }
}
