package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell

/** The §6 gutter between the board pane's edge and the grid. */
private val PaneGutter: Dp = Spacing.md

// AdaptivePlayScaffold's TwoPane board weight; SudokuSizing derives the side slot from it.
private const val TWO_PANE_BOARD_WEIGHT = 0.62f

/**
 * The play screen. SinglePane stacks the grid over the keypad in the board pane, with the
 * chrome below; TwoPane gives the board pane to the grid alone and moves the keypad into
 * the chrome's game controls. Clear sits in the game controls either way.
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
    // One result hand-off per completion: keyed on completedAt, so a replay's win hands off again.
    LaunchedEffect(controller.board?.session?.completedAt) {
        val completed = controller.board?.session?.takeIf { it.isComplete } ?: return@LaunchedEffect
        onSolved(completed.elapsedMs, completed.hintsUsed)
    }
    val ready = status as? PlayBoardStatus.Ready
    val state = controller.board?.takeIf { ready != null }
    when (layout) {
        PlayLayout.SinglePane -> AdaptivePlayScaffold(
            layout = layout,
            modifier = modifier.fillMaxSize(),
            board = {
                SudokuBoardPane(status, onRetry) {
                    if (state != null) SudokuStackedBoard(state)
                }
            },
            side = {
                SudokuChrome(controller, title, ready, onHowToPlay) {
                    if (state != null) SudokuClearButton(state)
                }
            },
        )
        PlayLayout.TwoPane -> BoxWithConstraints(modifier.fillMaxSize()) {
            val sizing = state?.let {
                SudokuSizing.layout(
                    maxWidth = (maxWidth - DividerDefaults.Thickness) * TWO_PANE_BOARD_WEIGHT - PaneGutter * 2,
                    maxHeight = maxHeight - PaneGutter * 2,
                    size = it.session.board.geometry.size,
                    keypadBeside = true,
                )
            }
            AdaptivePlayScaffold(
                layout = layout,
                modifier = Modifier.fillMaxSize(),
                board = {
                    SudokuBoardPane(status, onRetry) {
                        if (state != null && sizing != null) {
                            SudokuGrid(state.model, state::select, Modifier.size(sizing.board))
                        }
                    }
                },
                side = {
                    SudokuChrome(controller, title, ready, onHowToPlay) {
                        if (state != null && sizing != null) {
                            SudokuBoardKeypad(state, sizing.keyRows)
                            SudokuClearButton(state)
                        }
                    }
                },
            )
        }
    }
}

/**
 * Grid over keypad, sized by [SudokuSizing] for the space given. The tutorial draws only
 * this, so its lessons play on the same keypad as the daily.
 */
@Composable
internal fun SudokuStackedBoard(state: SudokuBoardState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val layout = SudokuSizing.layout(maxWidth, maxHeight, state.session.board.geometry.size, keypadBeside = false)
        Column(
            modifier = Modifier.testTag("sudoku.board"),
            verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SudokuGrid(state.model, state::select, Modifier.size(layout.board))
            SudokuBoardKeypad(state, layout.keyRows, Modifier.widthIn(max = PlayBoardMaxWidth))
        }
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
private fun SudokuBoardKeypad(state: SudokuBoardState, rows: Int, modifier: Modifier = Modifier) {
    val session = state.session
    SudokuKeypad(
        model = sudokuKeypadModel(session.board, session.notesMode),
        onDigit = state::input,
        onErase = state::erase,
        onToggleNotes = state::toggleNotesMode,
        modifier = modifier.testTag("sudoku.keypad"),
        rows = rows,
    )
}

@Composable
private fun SudokuChrome(
    controller: SudokuPlayController,
    title: String,
    ready: PlayBoardStatus.Ready?,
    onHowToPlay: (() -> Unit)?,
    gameControls: @Composable ColumnScope.() -> Unit,
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
        gameControls = gameControls,
    )
}

/** One undoable command, so Clear needs no confirm; live while anything but the givens is on the board. */
@Composable
private fun SudokuClearButton(state: SudokuBoardState) {
    val session = state.session
    SecondaryButton(
        text = "Clear",
        onClick = state::clear,
        enabled = !session.isComplete && session.board.cells.any { it !is SudokuCell.Given && it != SudokuCell.Empty },
        modifier = Modifier.padding(vertical = Spacing.sm).testTag("sudoku.clear"),
    )
}
