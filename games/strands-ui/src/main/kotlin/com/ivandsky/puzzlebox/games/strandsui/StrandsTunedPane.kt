package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardPane
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
import com.ivandsky.puzzlebox.games.strandsui.chrome.HintRowPlacement
import com.ivandsky.puzzlebox.games.strandsui.chrome.StrandsHintRow

/**
 * The board pane under a play tuning (nav-rethink B5, tablet landscape; the phone's page
 * keeps its own scroll and shares only the hint row): just the board, no hint row, so the
 * scaffold can put the shared controls under it. The cell is [strandsCellSizeFor] over the
 * pane (never wider than [paneCap], never under the 48 dp floor), and the board's width goes
 * to [onBoardWidth] after layout, so the control row can be as wide as the board.
 */
@Composable
internal fun StrandsTunedBoardPane(
    controller: StrandsPlayController,
    status: PlayBoardStatus,
    paneCap: Dp?,
    onRetry: () -> Unit,
    onBoardWidth: (Dp) -> Unit,
) {
    val model = controller.baseModel
    PlayBoardPane(Modifier.padding(horizontal = Spacing.lg).testTag("strands.board_area")) {
        if (status !is PlayBoardStatus.Ready || controller.header == null || model.rows == 0) {
            PlayBoardFallback(status, onRetry, tagPrefix = "strands")
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = strandsCellSizeFor(minOf(maxWidth, paneCap ?: maxWidth), maxHeight, model.rows, model.cols)
                val boardWidth = cell * model.cols
                SideEffect { onBoardWidth(boardWidth) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StrandsBoard(controller, Modifier.width(boardWidth))
                }
            }
        }
    }
}

/**
 * The control row under the board: the found counter and Hint in the shared row's slots
 * ([HintRowPlacement.Slots]), as wide as the board ([boardWidth], null until it is laid out).
 * [beside] is the landscape side column the scaffold stacks beside the board, where the
 * counter sits over Hint in the column's own width instead.
 */
@Composable
internal fun StrandsTunedControls(
    controller: StrandsPlayController,
    boardWidth: Dp?,
    beside: Boolean,
    reduceMotion: Boolean,
) {
    if (beside) {
        ControllerHintRow(controller, reduceMotion, HintRowPlacement.Column)
    } else {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            ControllerHintRow(controller, reduceMotion, HintRowPlacement.Slots, Modifier.widthIn(max = boardWidth ?: Dp.Infinity))
        }
    }
}

/**
 * The hint row for [controller]: the found count and the pill. A solved board has no hint to
 * give (NYT drops it too), so the counter stands alone.
 */
@Composable
internal fun ControllerHintRow(
    controller: StrandsPlayController,
    reduceMotion: Boolean,
    placement: HintRowPlacement,
    modifier: Modifier = Modifier,
) {
    val model = controller.baseModel
    StrandsHintRow(
        found = model.foundCount,
        total = model.totalCount,
        hint = controller.hint.takeUnless { controller.isComplete },
        onHint = controller::pressHint,
        reduceMotion = reduceMotion,
        modifier = modifier,
        placement = placement,
    )
}
