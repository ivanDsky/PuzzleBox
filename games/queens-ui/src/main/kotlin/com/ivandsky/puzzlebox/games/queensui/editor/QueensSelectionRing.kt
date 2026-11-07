package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import kotlin.math.roundToInt

/** The board's [size], its top-left [inRoot], and the [root]'s size: the window the ring must stay in. */
internal data class BoardFrame(val size: IntSize, val inRoot: Offset, val root: IntSize) {
    companion object {
        val Zero = BoardFrame(IntSize.Zero, Offset.Zero, IntSize.Zero)
    }
}

/**
 * The ring centred on [lift], pushed back inside the board and then inside the window; Scale in
 * + Fade in from the lift point. It lives in a non-focusable popup anchored to the board, so a
 * ring wider than the board (an 8×8 board on a small phone) keeps its full size, takes touches
 * outside the board's bounds, and draws above the context bar and tool strip, while a tap
 * beside it still reaches the board.
 */
@Composable
internal fun QueensSelectionRing(
    visible: Boolean,
    board: QueensDraft,
    selection: Set<Int>,
    lift: Offset,
    frame: BoardFrame,
    reducedMotion: Boolean,
    onPick: (Int) -> Unit,
) {
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (shown.isIdle && !shown.currentState) return
    val diameter = with(LocalDensity.current) { QueensEditorDefaults.ringDiameter(board.size).toPx() }
    val topLeft = ringTopLeft(lift, diameter, frame)
    val origin = TransformOrigin(
        ((lift.x - topLeft.x) / diameter).coerceIn(0f, 1f),
        ((lift.y - topLeft.y) / diameter).coerceIn(0f, 1f),
    )
    val used = remember(board) { board.regions.filterTo(HashSet()) { it != QueensDraft.UNPAINTED } }
    val current = selection.map { board.regions[it] }.distinct().singleOrNull()?.takeIf { it != QueensDraft.UNPAINTED }
    Popup(
        offset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = if (reducedMotion) {
                EnterTransition.None
            } else {
                val spec = tween<Float>(QueensEditorDefaults.ringEnterMillis, easing = MotionTokens.EasingEmphasizedDecelerate)
                scaleIn(spec, QueensEditorDefaults.ringEnterScale, origin) + fadeIn(spec)
            },
            exit = if (reducedMotion) {
                ExitTransition.None
            } else {
                val spec = tween<Float>(QueensEditorDefaults.ringExitMillis, easing = MotionTokens.EasingEmphasizedAccelerate)
                scaleOut(spec, QueensEditorDefaults.ringExitScale, origin) + fadeOut(spec)
            },
        ) {
            QueensColourRing(colours = board.size, used = used, current = current, onPick = onPick)
        }
    }
}

/** The ring's top-left in board pixels: centred on [lift], inside the board, then inside the root. */
internal fun ringTopLeft(lift: Offset, diameter: Float, frame: BoardFrame): Offset {
    val half = diameter / 2
    fun axis(at: Float, board: Int, origin: Float, root: Int): Float =
        clampCentre(clampCentre(at, half, board.toFloat()) + origin, half, root.toFloat()) - origin - half
    return Offset(
        axis(lift.x, frame.size.width, frame.inRoot.x, frame.root.width),
        axis(lift.y, frame.size.height, frame.inRoot.y, frame.root.height),
    )
}

/** Keeps a [half]-wide ring inside [extent]; an extent smaller than the ring centres it. */
private fun clampCentre(at: Float, half: Float, extent: Float): Float =
    if (extent < 2 * half) extent / 2 else at.coerceIn(half, extent - half)
