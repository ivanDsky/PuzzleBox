package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.swipeAxis
import kotlinx.coroutines.CancellationException

/**
 * The deck's one finger: a press let go inside [RecapMotion.HoldThreshold] is a tap ([onTap] with where it lifted), a
 * longer one holds ([onHold] true, then false when it lifts or is taken away), and a horizontal drag past the touch slop
 * follows the finger ([onDrag] per delta, [onDragEnd] with the whole travel and the release velocity, px and px/s, or no
 * travel when the touch is taken away). A vertical drag is the card's own scroll and is left alone; so is a tap or hold
 * a button inside the card took.
 */
internal suspend fun PointerInputScope.detectDeckGestures(
    onTap: (Offset) -> Unit,
    onHold: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (dragPx: Float, velocityPx: Float) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val velocity = VelocityTracker().apply { addPointerInputChange(down) }
    val press = withTimeoutOrNull(RecapMotion.HoldThreshold.toLong()) { awaitPress(down.id, velocity) }
    when {
        press is Press.Drag -> drag(down.id, press.travel, velocity, onDrag, onDragEnd)
        down.isConsumed -> Unit
        press is Press.Tap -> onTap(press.at)
        press == null -> hold(down.id, onHold)
    }
}

private sealed interface Press {
    data class Tap(val at: Offset) : Press
    data class Drag(val travel: Float) : Press
    data object LeftAlone : Press
}

private suspend fun AwaitPointerEventScope.awaitPress(id: PointerId, velocity: VelocityTracker): Press {
    var travel = Offset.Zero
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return Press.LeftAlone
        velocity.addPointerInputChange(change)
        if (change.isConsumed) return Press.LeftAlone
        if (!change.pressed) return Press.Tap(change.position)
        travel += change.positionChange()
        when (swipeAxis(travel.x, travel.y, viewConfiguration.touchSlop)) {
            Orientation.Horizontal -> {
                change.consume()
                return Press.Drag(travel.x)
            }
            Orientation.Vertical -> return Press.LeftAlone
            null -> Unit
        }
    }
}

private suspend fun AwaitPointerEventScope.hold(id: PointerId, onHold: (Boolean) -> Unit) {
    onHold(true)
    try {
        do {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == id }
        } while (change != null && change.pressed)
    } finally {
        onHold(false)
    }
}

private suspend fun AwaitPointerEventScope.drag(
    id: PointerId,
    travel: Float,
    velocity: VelocityTracker,
    onDrag: (Float) -> Unit,
    onDragEnd: (dragPx: Float, velocityPx: Float) -> Unit,
) {
    var dragged = travel
    onDrag(travel)
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id }
            if (change == null || !change.pressed) break
            velocity.addPointerInputChange(change)
            val dx = change.positionChange().x
            dragged += dx
            onDrag(dx)
            event.changes.forEach { it.consume() }
        }
    } catch (cancelled: CancellationException) {
        // A touch taken away (a palm, a system gesture) is no swipe, but the card must not stay where the finger left it.
        onDragEnd(0f, 0f)
        throw cancelled
    }
    onDragEnd(dragged, velocity.calculateVelocity().x)
}
