package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypad
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypadActions
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypadModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuLayout
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSizing

/**
 * The tools row under the grid, [height] tall: Erase, Clear, and a third slot kept free for
 * the symmetry toggle. Erase stays on with nothing selected (it then nudges); Clear is off while the
 * board holds no digit.
 */
@Composable
internal fun SudokuEditorTools(
    controller: SudokuEditorController,
    canClear: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().widthIn(max = PlayBoardMaxWidth),
        horizontalArrangement = Arrangement.spacedBy(SudokuSizing.keyGap),
    ) {
        val tool = Modifier.weight(1f).height(height)
        GameButton(
            onClick = controller::erase,
            icon = PuzzleIcons.Erase,
            label = "Erase",
            modifier = tool.testTag("sudoku.editor.erase"),
        )
        GameButton(
            onClick = controller::clear,
            icon = PuzzleIcons.Clear,
            label = "Clear",
            modifier = tool.testTag("sudoku.editor.clear"),
            enabled = canClear,
        )
        Spacer(tool)
    }
}

/**
 * The play keypad's digit keys over [draft]: each shows how many of its digit are placed and
 * is tagged `sudoku.editor.key.<d>`. Notes, Erase and Clear are not drawn (see
 * [SudokuEditorTools]).
 */
@Composable
internal fun SudokuEditorKeypad(
    controller: SudokuEditorController,
    draft: SudokuDraft,
    layout: SudokuLayout,
    modifier: Modifier = Modifier,
) {
    val placed = (1..draft.size).map { digit -> draft.givens.count { it == digit } }
    val remaining = placed.map { (draft.size - it).coerceAtLeast(0) }
    SudokuKeypad(
        model = SudokuKeypadModel(draft.size, remaining, notesMode = false, canClear = false, solved = false),
        onDigit = controller::digit,
        onErase = {},
        onClear = {},
        onToggleNotes = {},
        modifier = modifier.widthIn(max = PlayBoardMaxWidth).testTag("sudoku.editor.keypad"),
        keyHeight = layout.key,
        actionHeight = layout.actionKey,
        actions = SudokuKeypadActions.Row,
        showActions = false,
        placed = placed,
        digitTag = { "sudoku.editor.key.$it" },
    )
}
