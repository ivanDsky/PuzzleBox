package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PhoneBoardCap
import com.ivandsky.puzzlebox.gameui.PlayBoardPane
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * The play screen under the route's top bar (title, clock, How to play). Both layouts
 * stack the grid over the keypad, so the digits stay one short reach from the cells.
 * SinglePane docks the shared chrome and action bar below. TwoPane (tablet landscape)
 * puts the control row in a column beside the board; the side pane keeps the shared chrome.
 * The keypad is the Corner pad (a small Notes toggle over the digits, Erase beside it, or in
 * the side column on TwoPane) and Clear is in the control row.
 * The result hand-off waits for the solve sweep ([SudokuMotionHost]); one per board, so a
 * play-again's win hands off again. [brand] colours the solve moment's frame.
 */
@Composable
internal fun SudokuPlayScreen(
    controller: SudokuPlayController,
    status: PlayBoardStatus,
    layout: PlayLayout,
    sidePanel: (@Composable ColumnScope.() -> Unit)?,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
    brand: GameBrand = MiniSudokuBrand,
) {
    val ready = status as? PlayBoardStatus.Ready
    val state = controller.board?.takeIf { ready != null }
    val twoPane = layout == PlayLayout.TwoPane
    // The side column carries Erase on TwoPane; SinglePane keeps it on the keypad's strip.
    // The control row spans the grid's columns, and the grid is narrower than the pane cap.
    // Reported after layout, so the first frame is wide.
    var gridWidth by remember { mutableStateOf<Dp?>(null) }
    SudokuMotionHost(controller.board, onSolved = { onSolved(it.elapsedMs, it.hintsUsed) }, brand = brand) {
        AdaptivePlayScaffold(
            layout = layout,
            gridColumns = state?.session?.board?.geometry?.size,
            modifier = modifier.fillMaxSize(),
            board = { paneCap ->
                SudokuBoardPane(status, onRetry) {
                    if (state != null) {
                        SudokuStackedBoard(state, paneCap = paneCap, onGridWidth = { gridWidth = it }, tuned = true, eraseBeside = twoPane)
                    }
                }
            },
            side = {
                PlayChrome(
                    clock = controller,
                    hints = controller,
                    feedback = controller,
                )
            },
            actions = { SudokuTunedControls(controller, state, gridWidth, beside = twoPane) },
            sidePanel = sidePanel,
        )
    }
}

/**
 * Grid over keypad, sized by [SudokuSizing] for the space given. The tutorial draws only
 * this, so its lessons play on the same keypad as the daily. The grid's width goes out through
 * [onGridWidth]. [paneCap] is the board cap; null keeps [PhoneBoardCap]. [tuned] (the play screen's
 * layout; the tutorial draws it untuned) keeps every key at the 48dp touch floor, shrinking the grid
 * first, and takes Clear out of the keypad: it sits in the control row.
 * The grid alone wears the solve moment's frame ([LocalSudokuSolveFrame]) and the game's glyph stamp ([LocalSudokuSolveStamp]),
 * so it pops and the keypad stays put. The grid draws the board state's own staged hint over its cells (the keypad never is
 * covered); [overlay] is the Trainer's own layer over them, handed the grid's digit measuring ([SudokuGrid]).
 */
@Composable
internal fun SudokuStackedBoard(
    state: SudokuBoardState,
    modifier: Modifier = Modifier,
    paneCap: Dp? = null,
    onGridWidth: (Dp) -> Unit = {},
    tuned: Boolean = false,
    eraseBeside: Boolean = false,
    overlay: (@Composable BoxScope.(restyle: (Spotlight) -> Spotlight) -> Unit)? = null,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = state.session.board.geometry.size
        val floor = if (tuned) SudokuSizing.keyMin else 0.dp
        val layout = SudokuSizing.layout(maxWidth, maxHeight, size, inlineNotes = false, boardMax = paneCap ?: PhoneBoardCap, keyFloor = floor, corner = tuned)
        SideEffect { onGridWidth(layout.board) }
        Column(
            modifier = Modifier.testTag("sudoku.board"),
            verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(layout.board).then(LocalSudokuSolveFrame.current), contentAlignment = Alignment.Center) {
                SudokuGrid(state.model, state::select, Modifier.fillMaxSize(), spotlight = state.spotlight, overlay = overlay)
                LocalSudokuSolveStamp.current?.invoke()
            }
            // A pane-filling grid is height-bound: keys as wide as the pane would be slabs.
            SudokuBoardKeypad(state, layout, Modifier.widthIn(max = if (paneCap != null) layout.board else PhoneBoardCap), showErase = !eraseBeside)
        }
    }
}

/** The board pane: the board once a puzzle is ready ([status] Ready), the shared fallback otherwise. */
@Composable
private fun SudokuBoardPane(status: PlayBoardStatus, onRetry: () -> Unit, board: @Composable () -> Unit) {
    PlayBoardPane(Modifier.padding(horizontal = Spacing.lg).testTag("sudoku.board_area")) {
        if (status is PlayBoardStatus.Ready) board() else PlayBoardFallback(status, onRetry, tagPrefix = "sudoku")
    }
}

@Composable
private fun SudokuBoardKeypad(state: SudokuBoardState, layout: SudokuLayout, modifier: Modifier = Modifier, showErase: Boolean = true) {
    val session = state.session
    SudokuKeypad(
        model = sudokuKeypadModel(session.board, session.notesMode),
        onDigit = state::input,
        onErase = state::erase,
        onClear = state::clear,
        onToggleNotes = state::toggleNotesMode,
        modifier = modifier.testTag("sudoku.keypad"),
        keyHeight = layout.key,
        actionHeight = layout.actionKey,
        actions = layout.actions,
        showErase = showErase,
    )
}
