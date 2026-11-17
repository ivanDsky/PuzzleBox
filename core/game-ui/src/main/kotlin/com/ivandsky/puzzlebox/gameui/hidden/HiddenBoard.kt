package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** What TalkBack hears for the whole slot: never a cell. */
internal const val HIDDEN_BOARD_DESCRIPTION = "Board hidden until you play"
internal const val SHARP_BOARD_DESCRIPTION = "Board"

/**
 * A puzzle's own board in a slot of its shape, shown before the puzzle is played. Unless [sharp], the board is
 * blurred past reading under a veil and a pill that says "Hidden until you play", so a puzzle can be told apart
 * by its size and colours and nothing more. [sharp] shows it plain, for a puzzle whose board is no secret: one
 * you made, or one you have played.
 *
 * Either way no touch reaches the board, so nothing on it can be tapped, held or played, and TalkBack is told one
 * line. The slot keeps no touch for itself: a drag that starts on the board still scrolls the page it is in.
 * Turning [sharp] on fades the blur and the veil away over the plain board. The slot is at most
 * [HiddenBoardTokens.MaxSide] high unless a page with room to spare gives it a larger [maxSide].
 */
@Composable
fun HiddenBoard(board: GivensBoard, sharp: Boolean, modifier: Modifier = Modifier, maxSide: Dp = HiddenBoardTokens.MaxSide) {
    val reduced = rememberReducedMotion()
    val reveal = updateTransition(targetState = sharp, label = "hidden-board")
    val sharpAlpha by reveal.animateFloat({ fade(reduced, MotionTokens.DurationMedium) }, "sharp") { if (it) 1f else 0f }
    val pillAlpha by reveal.animateFloat({ fade(reduced, MotionTokens.DurationExit) }, "pill") { if (it) 0f else 1f }
    val showSharp = reveal.currentState || reveal.targetState
    val showBlurred = !(reveal.currentState && reveal.targetState)

    HiddenBoardSlot(
        rows = board.rows,
        cols = board.cols,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (sharp) SHARP_BOARD_DESCRIPTION else HIDDEN_BOARD_DESCRIPTION
        },
        maxSide = maxSide,
    ) {
        if (showSharp) {
            Fitted(Modifier.graphicsLayer { alpha = sharpAlpha }) { board.content(Modifier) }
        }
        if (showBlurred) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - sharpAlpha }) {
                Fitted(Modifier.hiddenBoardBlur()) { board.content(Modifier) }
                HiddenBoardVeil(pillAlpha = { pillAlpha })
            }
        }
        // A layer above the board that is hit first, so the board under it is never asked. It uses up nothing:
        // what lands on it goes on to whatever holds the slot, which is how a page under it scrolls.
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

/** The board at the slot's size: games size their board from the width they are given. */
@Composable
private fun BoxScope.Fitted(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.matchParentSize(), contentAlignment = Alignment.Center, propagateMinConstraints = true) { content() }
}

private fun fade(reduced: Boolean, millis: Int): FiniteAnimationSpec<Float> =
    if (reduced) tween(MotionTokens.ReducedFadeDuration) else tween(millis, easing = MotionTokens.EasingEmphasizedDecelerate)
