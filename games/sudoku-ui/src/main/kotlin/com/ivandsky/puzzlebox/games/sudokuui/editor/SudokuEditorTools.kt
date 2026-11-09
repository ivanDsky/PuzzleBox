package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameToggleButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudokuui.MiniSudokuBrand
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypad
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypadActions
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypadModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuLayout
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSizing
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth

/**
 * The tools row under the grid, [height] tall: Erase, Clear, the Symmetry toggle and the
 * Forced cells switch (filled with the Sudoku accent while on; on shows the digits logic
 * places from the givens). Erase stays on with nothing selected (it then nudges); Clear is
 * off while the board holds no digit.
 * Symmetry shows its mode as state and, while [unpaired] cells wait for a partner, their
 * count in a badge.
 */
@Composable
internal fun SudokuEditorTools(
    controller: SudokuEditorController,
    canClear: Boolean,
    unpaired: Int,
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
        SymmetryButton(controller, unpaired, tool)
        ForcedButton(controller, tool)
    }
}

@Composable
private fun ForcedButton(controller: SudokuEditorController, modifier: Modifier) {
    val on by controller.forcedView.collectAsState()
    GameToggleButton(
        checked = on,
        onCheckedChange = { controller.toggleForcedView() },
        icon = PuzzleIcons.Hint,
        label = "Forced",
        accent = MiniSudokuBrand.accent,
        modifier = modifier.testTag("sudoku.editor.forced"),
    )
}

@Composable
private fun SymmetryButton(controller: SudokuEditorController, unpaired: Int, modifier: Modifier) {
    val symmetry by controller.symmetry.collectAsState()
    Box(modifier) {
        GameButton(
            onClick = controller::cycleSymmetry,
            icon = PuzzleIcons.Appearance,
            label = "Symmetry",
            modifier = Modifier.fillMaxSize().testTag("sudoku.editor.symmetry").semantics {
                stateDescription = symmetry.name
            },
        )
        if (unpaired > 0) {
            Text(
                text = unpaired.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                    .padding(horizontal = 6.dp)
                    .testTag("sudoku.editor.unpaired"),
            )
        }
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
        model = SudokuKeypadModel(draft.size, remaining, notesMode = false, solved = false),
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
