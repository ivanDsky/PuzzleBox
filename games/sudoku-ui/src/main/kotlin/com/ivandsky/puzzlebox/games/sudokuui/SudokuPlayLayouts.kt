package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayHints
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayUndo

/** The §6 gutter between the board pane's edge and the grid. */
private val PaneGutter: Dp = Spacing.md

/**
 * The play screen. Both layouts stack the grid over the keypad in the board pane, so the
 * digits stay one short reach from the cells; the chrome goes below (SinglePane) or beside
 * (TwoPane). TwoPane's side pane is a reach away, so there Undo/Redo and Hint join the
 * keypad as a row of its own and the side pane keeps the title, timer, caption and ⓘ.
 * Clear rides the keypad's action row, so the chrome takes no row of its own.
 * The result hand-off waits for the solve sweep ([SudokuMotionHost]); one per board, so a
 * replay's win hands off again.
 */
@Composable
internal fun SudokuPlayScreen(
    controller: SudokuPlayController,
    title: String,
    status: PlayBoardStatus,
    layout: PlayLayout,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    onHowToPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ready = status as? PlayBoardStatus.Ready
    val state = controller.board?.takeIf { ready != null }
    val actionsOnBoard = layout == PlayLayout.TwoPane
    SudokuMotionHost(controller.board, onSolved = { onSolved(it.elapsedMs, it.hintsUsed) }) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = modifier.fillMaxSize(),
            board = {
                SudokuBoardPane(status, onRetry) {
                    if (state != null) {
                        SudokuStackedBoard(
                            state,
                            controls = if (actionsOnBoard) { height -> SudokuPlayControls(controller, controller, height) } else null,
                        )
                    }
                }
            },
            side = { SudokuChrome(controller, title, ready, onHowToPlay, actionsOnBoard) },
        )
    }
}

/**
 * Grid over keypad, sized by [SudokuSizing] for the space given. The tutorial draws only
 * this, so its lessons play on the same keypad as the daily. [controls], when given, is
 * one more key-tall row between the grid and the keypad (TwoPane's Undo/Redo/Hint).
 */
@Composable
internal fun SudokuStackedBoard(
    state: SudokuBoardState,
    modifier: Modifier = Modifier,
    controls: (@Composable (height: Dp) -> Unit)? = null,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val actionRows = if (controls != null) 2 else 1
        val layout = SudokuSizing.layout(maxWidth, maxHeight, state.session.board.geometry.size, actionRows)
        Column(
            modifier = Modifier.testTag("sudoku.board"),
            verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SudokuGrid(state.model, state::select, Modifier.size(layout.board))
            Column(
                modifier = Modifier.widthIn(max = PlayBoardMaxWidth),
                verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            ) {
                controls?.invoke(layout.key)
                SudokuBoardKeypad(state, layout.keyRows, layout.key)
            }
        }
    }
}

/** Undo, Redo and Hint as tonal buttons matching the keypad's action row, one key tall. */
@Composable
private fun SudokuPlayControls(undo: PlayUndo, hints: PlayHints, height: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("sudoku.play_controls"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledTonalButton(
            onClick = undo::undo,
            enabled = undo.canUndo,
            modifier = Modifier.weight(1f).height(height).testTag("play.undo"),
        ) { Text("Undo") }
        FilledTonalButton(
            onClick = undo::redo,
            enabled = undo.canRedo,
            modifier = Modifier.weight(1f).height(height).testTag("play.redo"),
        ) { Text("Redo") }
        FilledTonalButton(
            onClick = hints::requestHint,
            enabled = hints.hintEnabled,
            modifier = Modifier.weight(1f).height(height),
        ) { Text(if (hints.hintsUsed == 0) "Hint" else "Hint · ${hints.hintsUsed}") }
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
private fun SudokuBoardKeypad(state: SudokuBoardState, rows: Int, keyHeight: Dp) {
    val session = state.session
    SudokuKeypad(
        model = sudokuKeypadModel(session.board, session.notesMode),
        onDigit = state::input,
        onErase = state::erase,
        onClear = state::clear,
        onToggleNotes = state::toggleNotesMode,
        modifier = Modifier.testTag("sudoku.keypad"),
        rows = rows,
        keyHeight = keyHeight,
    )
}

@Composable
private fun SudokuChrome(
    controller: SudokuPlayController,
    title: String,
    ready: PlayBoardStatus.Ready?,
    onHowToPlay: (() -> Unit)?,
    actionsOnBoard: Boolean,
) {
    PlayChrome(
        title = title,
        subtitle = ready?.let { controller.board?.session?.ref?.puzzleNumber }?.let { "Daily · #$it" } ?: "Daily",
        clock = controller,
        hints = controller,
        undo = controller,
        banner = ready?.banner,
        feedback = controller,
        onHowToPlay = onHowToPlay,
        actionsOnBoard = actionsOnBoard,
    )
}
