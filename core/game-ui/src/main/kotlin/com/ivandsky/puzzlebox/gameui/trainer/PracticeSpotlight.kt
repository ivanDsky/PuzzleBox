package com.ivandsky.puzzlebox.gameui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightCallout
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.designsystem.spotlight.StepFlash
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Spotlight layer a game's [TrainerGame.Board] mounts over its cell grid, after the grid so it sits on top.
 * A [PracticeStatus.Right] step only flashes and leaves the board playable. An explanation ([PracticeStatus.Easier],
 * [PracticeStatus.Shown]) draws the full Spotlight and takes every touch on the grid, so no move lands under it;
 * a tap on it calls [PracticeBoard.gotIt]. Its bounds are the grid's bounds.
 */
@Composable
fun BoxScope.PracticeSpotlight(board: PracticeBoard, status: PracticeStatus) {
    when (status) {
        is PracticeStatus.Right -> StepFlash(status.spotlight)
        is PracticeStatus.Easier -> Explanation(board) { SpotlightOverlay(status.spotlight) }
        is PracticeStatus.Shown -> Explanation(board) { SpotlightOverlay(status.spotlight) }
        PracticeStatus.FindNextMove, is PracticeStatus.KeepGoing, is PracticeStatus.Wrong, PracticeStatus.Solved -> Unit
    }
}

/** [spotlight] drawn, with a layer over it that swallows every touch on the grid and calls [PracticeBoard.gotIt] on release. */
@Composable
private fun BoxScope.Explanation(board: PracticeBoard, spotlight: @Composable BoxScope.() -> Unit) {
    spotlight()
    Box(
        Modifier
            .matchParentSize()
            .testTag("train.explanation")
            .pointerInput(board) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.type == PointerEventType.Release) board.gotIt()
                    }
                }
            },
    )
}

// Preview: a Show me explanation over a blank 5×5 grid, the state that holds the board until Got it.
@PuzzleBoxPreviews
@Composable
internal fun PracticeSpotlightPreview() {
    val status = PracticeStatus.Shown(
        Spotlight(5, 5, mapOf(12 to SpotlightRole.TARGET), SpotlightCallout("Last cell: one open cell left.", setOf(12)), PreviewDisc, playId = 0),
    )
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.md).size(160.dp).background(LocalBoardColors.current.boardSurface)) {
            PracticeSpotlight(PreviewPracticeBoard(status), status)
        }
    }
}

/** A board that only reports [shown]; the preview never plays it. */
private class PreviewPracticeBoard(shown: PracticeStatus) : PracticeBoard {
    override val technique: TechniqueId = TechniqueId("last-cell")
    override val status: StateFlow<PracticeStatus> = MutableStateFlow(shown)
    override fun showMe() = Unit
    override fun gotIt() = Unit
}

/** Stand-in game glyph: a disc. */
private data object PreviewDisc : SpotlightGlyphs {
    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        drawCircle(color, size.minDimension * 0.3f, Offset(size.width / 2, size.height / 2))
    }
}
