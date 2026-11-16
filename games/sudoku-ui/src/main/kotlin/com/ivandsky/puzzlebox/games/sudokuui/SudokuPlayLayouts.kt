package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.LocalPlayTuning
import com.ivandsky.puzzlebox.gameui.PlayActionBar
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardPane
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout

/** The §6 gutter between the board pane's edge and the grid. */
private val PaneGutter: Dp = Spacing.md

/**
 * The play screen under the route's top bar (title, clock, How to play). Both layouts
 * stack the grid over the keypad, so the digits stay one short reach from the cells.
 * SinglePane docks the shared chrome and action bar below. TwoPane (tablet landscape)
 * fills the board pane with the grid over the digit keys, the notes toggle beside them,
 * under a toolbar of the action bar with Erase and Clear (A15), so the board gets the
 * height of the button row it no longer carries; the side pane keeps the shared chrome.
 * The action bar's Clear is hidden: the keypad (or the TwoPane toolbar) has Sudoku's own.
 * Under a play tuning the keypad is the Corner pad (a small Notes toggle over the digits, Erase
 * beside it, or in the side column when the controls stack there) and Clear is in the control row.
 * The result hand-off waits for the solve sweep ([SudokuMotionHost]); one per board, so a
 * play-again's win hands off again.
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
) {
    val ready = status as? PlayBoardStatus.Ready
    val state = controller.board?.takeIf { ready != null }
    val twoPane = layout == PlayLayout.TwoPane
    val tuning = LocalPlayTuning.current
    val tuned = tuning != null
    // The tuned side column carries Erase; every other tuned layout keeps it on the keypad's strip.
    val eraseBeside = twoPane && tuning?.landscapeControls == LandscapeControls.SideColumn
    // The toolbar spans the grid's columns, and the grid is narrower than the pane cap: it
    // shares the height with the keypad. Reported after layout, so the first frame is wide.
    var gridWidth by remember { mutableStateOf<Dp?>(null) }
    SudokuMotionHost(controller.board, onSolved = { onSolved(it.elapsedMs, it.hintsUsed) }) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = modifier.fillMaxSize(),
            board = { paneCap ->
                SudokuBoardPane(status, onRetry, tuned) {
                    if (state != null) {
                        SudokuStackedBoard(state, inlineNotes = twoPane && !tuned, paneCap = paneCap, onGridWidth = { gridWidth = it }, tuned = tuned, eraseBeside = eraseBeside)
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
            actions = {
                if (tuning != null) {
                    SudokuTunedControls(controller, state, gridWidth, beside = twoPane && tuning.landscapeControls == LandscapeControls.SideColumn)
                } else if (twoPane) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Row(Modifier.widthIn(max = gridWidth ?: Dp.Infinity), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            // 3:2 keeps all five buttons one width.
                            PlayActionBar(controller, controller, clear = null, MiniSudokuBrand.accent, Modifier.weight(3f))
                            SudokuEditControls(state, Modifier.weight(2f))
                        }
                    }
                } else {
                    PlayActionBar(controller, controller, clear = null, MiniSudokuBrand.accent)
                }
            },
            sidePanel = sidePanel,
        )
    }
}

/**
 * Grid over keypad, sized by [SudokuSizing] for the space given. The tutorial draws only
 * this, so its lessons play on the same keypad as the daily. [inlineNotes] puts the notes
 * toggle beside the digits and leaves Erase and Clear to the caller (TwoPane), told the
 * grid's width through [onGridWidth]. [paneCap] is the two-pane board cap; null keeps
 * [PlayBoardMaxWidth]. [tuned] (a play tuning is set) keeps every key at the 48dp touch
 * floor, shrinking the grid first, and takes Clear out of the keypad: it sits in the control row.
 */
@Composable
internal fun SudokuStackedBoard(
    state: SudokuBoardState,
    modifier: Modifier = Modifier,
    inlineNotes: Boolean = false,
    paneCap: Dp? = null,
    onGridWidth: (Dp) -> Unit = {},
    tuned: Boolean = false,
    eraseBeside: Boolean = false,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = state.session.board.geometry.size
        val floor = if (tuned) SudokuSizing.keyMin else 0.dp
        val layout = SudokuSizing.layout(maxWidth, maxHeight, size, inlineNotes, boardMax = paneCap ?: PlayBoardMaxWidth, keyFloor = floor, corner = tuned)
        SideEffect { onGridWidth(layout.board) }
        Column(
            modifier = Modifier.testTag("sudoku.board"),
            verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SudokuGrid(state.model, state::select, Modifier.size(layout.board), spotlight = state.spotlight)
            // A pane-filling grid is height-bound: keys as wide as the pane would be slabs.
            SudokuBoardKeypad(state, layout, Modifier.widthIn(max = if (paneCap != null) layout.board else PlayBoardMaxWidth), showErase = !eraseBeside)
        }
    }
}

/**
 * TwoPane's Erase and Clear, beside the action bar in the toolbar and the same tall
 * chunky buttons. Erase is off until a board is ready and once it is solved; Clear is
 * never off (a no-op on an empty or solved board), like every other game's Clear.
 */
@Composable
private fun SudokuEditControls(state: SudokuBoardState?, modifier: Modifier = Modifier) {
    val model = state?.session?.let { sudokuKeypadModel(it.board, it.notesMode) }
    val editable = model != null && !model.solved
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            size = GameButtonSize.Tall,
        )
    }
}

/**
 * The board pane: the board once a puzzle is ready ([status] Ready), the shared fallback otherwise.
 * [tuned] (a play tuning is set) makes it only as tall as its content, so the scaffold can put
 * the control row directly under the grid and keypad.
 */
@Composable
private fun SudokuBoardPane(status: PlayBoardStatus, onRetry: () -> Unit, tuned: Boolean, board: @Composable () -> Unit) {
    val content: @Composable () -> Unit = { if (status is PlayBoardStatus.Ready) board() else PlayBoardFallback(status, onRetry, tagPrefix = "sudoku") }
    if (tuned) {
        PlayBoardPane(Modifier.padding(horizontal = Spacing.lg).testTag("sudoku.board_area")) { content() }
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .padding(PaneGutter)
                .testTag("sudoku.board_area"),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
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
