package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.gameui.PlayActionBar
import com.ivandsky.puzzlebox.gameui.PlayClear

/** Four touch-floor buttons and their three gaps: the narrowest the shared control row keeps. */
private val ControlRowMinWidth: Dp = SudokuSizing.keyMin * 4 + SudokuSizing.keyGap * 3

/**
 * The shared Undo · Redo · Hint · Clear under a play tuning (nav-rethink B5), the only place
 * Sudoku's Clear is now. The scaffold hands the row the board cap's width, but the grid and
 * keypad above it can be narrower (a short pane shrinks the grid), so the row follows
 * [gridWidth] (null until the first layout: as wide as the pane gives), never under
 * [ControlRowMinWidth]. [beside] puts the same buttons in the column the scaffold stacks
 * beside the board (a landscape tablet's side column), where nothing narrows it, and adds
 * Erase between Hint and Clear in the same tall style; elsewhere Erase is on the keypad's strip.
 */
@Composable
internal fun SudokuTunedControls(
    controller: SudokuPlayController,
    state: SudokuBoardState?,
    gridWidth: Dp?,
    beside: Boolean,
    modifier: Modifier = Modifier,
) {
    val bar: @Composable () -> Unit = {
        val editable = state?.session?.let { !sudokuKeypadModel(it.board, it.notesMode).solved } ?: false
        PlayActionBar(controller, controller, PlayClear { state?.clear() }, MiniSudokuBrand.accent) {
            EraseButton(editable, { state?.erase() }, it.testTag("sudoku.erase"), GameButtonSize.Tall)
        }
    }
    if (beside) {
        Box(modifier) { bar() }
    } else {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(min = ControlRowMinWidth, max = maxOf(gridWidth ?: Dp.Infinity, ControlRowMinWidth))) { bar() }
        }
    }
}
