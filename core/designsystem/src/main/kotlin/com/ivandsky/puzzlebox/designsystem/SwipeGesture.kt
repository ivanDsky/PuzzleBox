package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlinx.coroutines.CancellationException

/**
 * How many boards a horizontal drag of [dragPx] released at [velocityPx] (both px, negative = leftwards)
 * walks: one forward for a leftward swipe, one back for a rightward one, none when it is neither a
 * fling ([flingPx]/s) nor a drag of [commitPx] ([SwipeMotion.CommitFraction] of [widthPx] unless the
 * caller caps it). A fling decides by its own direction.
 */
fun swipeStep(
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
fun swipeAxis(dx: Float, dy: Float, slop: Float): Orientation? = when {
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
suspend fun PointerInputScope.detectSwipe(onDrag: (Float) -> Unit, onEnd: (dragPx: Float, velocityPx: Float) -> Unit) =
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
