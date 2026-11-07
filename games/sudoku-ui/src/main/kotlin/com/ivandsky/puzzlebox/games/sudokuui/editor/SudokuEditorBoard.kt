package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import com.ivandsky.puzzlebox.games.sudokuui.SudokuBoardModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuCellModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuGrid
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypad
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypadModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSizing

/**
 * The editor's board: the play [SudokuGrid] over the [controller]'s draft, every placed digit
 * drawn as a given, with the play keypad below it. A tap on a cell selects it; a digit key
 * places on it and Erase empties it. Notes and Clear have no meaning for givens: Clear is off
 * (the Studio's own Clear covers it) and Notes is left inert. Sized by [SudokuSizing] for the
 * space [modifier] gives it.
 */
@Composable
internal fun SudokuEditorBoard(controller: SudokuEditorController, geometry: SudokuGeometry, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val selected by controller.selected.collectAsState()
    val board = draft.state.draft as SudokuDraft
    val model = remember(board, selected) { editorBoardModel(board, geometry, selected) }
    val keypad = remember(board) { editorKeypadModel(board) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val layout = SudokuSizing.layout(maxWidth, maxHeight, board.size, boardMax = PlayBoardMaxWidth)
        Column(
            modifier = Modifier.testTag("sudoku.editor.board"),
            verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SudokuGrid(model, controller::tap, Modifier.size(layout.board))
            SudokuKeypad(
                model = keypad,
                onDigit = controller::digit,
                onErase = { controller.digit(0) },
                onClear = {},
                onToggleNotes = {},
                modifier = Modifier.widthIn(max = PlayBoardMaxWidth).testTag("sudoku.editor.keypad"),
                keyHeight = layout.key,
                actionHeight = layout.actionKey,
                actions = layout.actions,
            )
        }
    }
}

/** Peers and same-digit cells of the selection are tinted like play's; a digit clashing with a peer is flagged. */
private fun editorBoardModel(draft: SudokuDraft, geometry: SudokuGeometry, selected: Int?): SudokuBoardModel {
    val cells = draft.givens.map { if (it == 0) SudokuCell.Empty else SudokuCell.Given(it) }
    val conflicts = SudokuConflicts.cellsIn(SudokuConflicts.find(SudokuBoard(geometry, cells)))
    val peers = selected?.let { geometry.peersOf(it).toSet() }.orEmpty()
    val selectedDigit = selected?.let { draft.givens[it] } ?: 0
    val models = draft.givens.mapIndexed { idx, digit ->
        SudokuCellModel(
            idx = idx,
            digit = digit,
            notes = 0,
            given = digit != 0,
            selected = idx == selected,
            peer = idx in peers,
            sameDigit = selectedDigit != 0 && idx != selected && digit == selectedDigit,
            conflict = idx in conflicts,
            hintTarget = false,
            hintUnit = false,
            hintStruck = false,
        )
    }
    return SudokuBoardModel(geometry.size, geometry.boxRows, geometry.boxCols, models)
}

private fun editorKeypadModel(draft: SudokuDraft): SudokuKeypadModel {
    val remaining = (1..draft.size).map { digit -> (draft.size - draft.givens.count { it == digit }).coerceAtLeast(0) }
    return SudokuKeypadModel(draft.size, remaining, notesMode = false, canClear = false, solved = false)
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 480, showBackground = true)
@Composable
internal fun SudokuEditorBoardPreview() {
    val editor = SudokuEditor(SudokuGames.MINI)
    val controller = remember {
        SudokuEditorController(EditorDraft.fresh(editor, editor.variants.first())) {}.apply {
            tap(0)
            digit(4)
            tap(1)
        }
    }
    PuzzleBoxTheme {
        SudokuEditorBoard(controller, SudokuGames.MINI.variant.geometry)
    }
}
