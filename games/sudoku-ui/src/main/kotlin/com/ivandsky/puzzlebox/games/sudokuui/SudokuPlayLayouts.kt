package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout

/** The §6 gutter between the board pane's edge and the grid. */
private val PaneGutter: Dp = Spacing.md

/**
 * The play screen under the route's top bar (title, clock, How to play). Both layouts
 * stack the grid over the keypad, so the digits stay one short reach from the cells.
 * SinglePane docks the shared chrome below. TwoPane (tablet landscape) splits in two
 * columns: the grid over the digit keys with the notes toggle beside them, and next to it
 * the shared chrome with Erase and Clear under it, so the board gets the height of the
 * button row it no longer carries. The action bar's Clear is hidden: the keypad (or the
 * TwoPane row) has Sudoku's own.
 * The result hand-off waits for the solve sweep ([SudokuMotionHost]); one per board, so a
 * replay's win hands off again.
 */
@Composable
internal fun SudokuPlayScreen(
    controller: SudokuPlayController,
    status: PlayBoardStatus,
    layout: PlayLayout,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = status as? PlayBoardStatus.Ready
    val state = controller.board?.takeIf { ready != null }
    val twoPane = layout == PlayLayout.TwoPane
    SudokuMotionHost(controller.board, onSolved = { onSolved(it.elapsedMs, it.hintsUsed) }) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = modifier.fillMaxSize(),
            board = {
                SudokuBoardPane(status, onRetry) {
                    if (state != null) SudokuStackedBoard(state, inlineNotes = twoPane)
                }
            },
            side = {
                Column(Modifier.testTag("sudoku.side")) {
                    PlayChrome(
                        clock = controller,
                        hints = controller,
                        undo = controller,
                        clear = null,
                        banner = ready?.banner,
                        feedback = controller,
                        accent = MiniSudokuBrand.accent,
                    )
                    if (twoPane) {
                        Box(Modifier.padding(horizontal = Spacing.lg)) { SudokuEditControls(state) }
                    }
                }
            },
        )
    }
}

/**
 * Grid over keypad, sized by [SudokuSizing] for the space given. The tutorial draws only
 * this, so its lessons play on the same keypad as the daily. [inlineNotes] puts the notes
 * toggle beside the digits and leaves Erase and Clear to the caller (TwoPane).
 */
@Composable
internal fun SudokuStackedBoard(state: SudokuBoardState, modifier: Modifier = Modifier, inlineNotes: Boolean = false) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val layout = SudokuSizing.layout(maxWidth, maxHeight, state.session.board.geometry.size, inlineNotes)
        Column(
            modifier = Modifier.testTag("sudoku.board"),
            verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SudokuGrid(state.model, state::select, Modifier.size(layout.board))
            SudokuBoardKeypad(state, layout, Modifier.widthIn(max = PlayBoardMaxWidth))
        }
    }
}

/**
 * TwoPane's Erase and Clear, under Undo/Redo in the side pane and the same tall chunky
 * buttons; off until a board is ready and once it is solved.
 */
@Composable
private fun SudokuEditControls(state: SudokuBoardState?) {
    val model = state?.session?.let { sudokuKeypadModel(it.board, it.notesMode) }
    val editable = model != null && !model.solved
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GameButton(
            onClick = { state?.erase() },
            icon = PuzzleIcons.Erase,
            label = "Erase",
            modifier = Modifier.weight(1f),
            enabled = editable,
            size = GameButtonSize.Tall,
        )
        GameButton(
            onClick = { state?.clear() },
            icon = PuzzleIcons.Clear,
            label = "Clear",
            modifier = Modifier.weight(1f).testTag("sudoku.clear"),
            enabled = editable && model?.canClear == true,
            size = GameButtonSize.Tall,
        )
    }
}

/** The board pane: the board once a puzzle is ready ([status] Ready), the shared fallback otherwise. */
@Composable
private fun SudokuBoardPane(status: PlayBoardStatus, onRetry: () -> Unit, board: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(PaneGutter)
            .testTag("sudoku.board_area"),
        contentAlignment = Alignment.Center,
    ) {
        if (status is PlayBoardStatus.Ready) board() else PlayBoardFallback(status, onRetry, tagPrefix = "sudoku")
    }
}

@Composable
private fun SudokuBoardKeypad(state: SudokuBoardState, layout: SudokuLayout, modifier: Modifier = Modifier) {
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
    )
}
