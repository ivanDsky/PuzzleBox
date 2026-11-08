package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.GameToggleButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * The Sudoku keypad: digit keys 1..N in three columns (6x6: 2 rows, 9x9: 3 rows), each
 * [keyHeight] tall (SudokuLayout.key), filling the caller's width. [actions] places Notes,
 * Erase and Clear (SudokuLayout.actions):
 * - [SudokuKeypadActions.Row]: three equal chunky game buttons under the digits, [actionHeight] tall.
 * - [SudokuKeypadActions.Column]: a fourth column beside the digits at key height, one per
 *   row; with two digit rows (6x6) Notes takes the first and Erase | Clear split the second.
 *   Key height is too short for icon over label, so these tiles are icon-only.
 * - [SudokuKeypadActions.NotesColumn] (TwoPane, whose side pane holds Erase and Clear): the
 *   notes toggle alone as an icon-only pencil key the digits' height in the fourth column.
 * Notes fills with the game's accent while on (A14).
 * Clear is one undoable command, so it needs no confirm; a solved board turns the actions off.
 * Switching notes morphs the digit keys one by one outward from the toggle (SudokuDigitKey).
 * The editor draws the digits alone ([showActions] false: its own tools row has Erase and
 * Clear, and Notes means nothing for givens), tags each key `digitTag(d)` and reads each
 * key's [placed] count (`placed[d - 1]`) instead of the digits left.
 */
@Composable
fun SudokuKeypad(
    model: SudokuKeypadModel,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onClear: () -> Unit,
    onToggleNotes: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = SudokuSizing.keyMin,
    actionHeight: Dp = SudokuSizing.keyMin,
    actions: SudokuKeypadActions = SudokuKeypadActions.Row,
    showActions: Boolean = true,
    placed: List<Int>? = null,
    digitTag: ((Int) -> String)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val rows = model.size / DIGIT_COLUMNS
    val gap = SudokuSizing.keyGap
    val editable = !model.solved
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val keyWidth = SudokuSizing.digitKeyWidth(maxWidth, rows, actions)
        val digits: @Composable () -> Unit = {
            DigitBlock(model, actions, Modifier.width(keyWidth).height(keyHeight), reducedMotion, placed, digitTag, onDigit)
        }
        if (!showActions) {
            digits()
        } else if (actions == SudokuKeypadActions.Row) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                digits()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    val action = Modifier.weight(1f).height(actionHeight)
                    NotesToggle(model.notesMode, editable, onToggleNotes, action)
                    EraseButton(editable, onErase, action)
                    ClearButton(editable && model.canClear, onClear, action)
                }
            }
        } else {
            val columnWidth = SudokuSizing.actionColumnWidth(maxWidth, rows, actions)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                digits()
                Column(Modifier.width(columnWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (actions == SudokuKeypadActions.NotesColumn) {
                        val key = Modifier.fillMaxWidth().height(keyHeight * rows + gap * (rows - 1)).testTag("sudoku.notes_key")
                        NotesToggle(model.notesMode, editable, onToggleNotes, key, GameButtonSize.Compact)
                    } else {
                        val tile = Modifier.fillMaxWidth().height(keyHeight)
                        NotesToggle(model.notesMode, editable, onToggleNotes, tile, GameButtonSize.Compact)
                        if (rows >= DIGIT_COLUMNS) {
                            EraseButton(editable, onErase, tile, GameButtonSize.Compact)
                            ClearButton(editable && model.canClear, onClear, tile, GameButtonSize.Compact)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                val half = Modifier.weight(1f).height(keyHeight)
                                EraseButton(editable, onErase, half, GameButtonSize.Compact)
                                ClearButton(editable && model.canClear, onClear, half, GameButtonSize.Compact)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val DIGIT_COLUMNS = 3

/** The digits row-major in three columns, every key [keyModifier]-sized. */
@Composable
private fun DigitBlock(
    model: SudokuKeypadModel,
    actions: SudokuKeypadActions,
    keyModifier: Modifier,
    reducedMotion: Boolean,
    placed: List<Int>?,
    digitTag: ((Int) -> String)?,
    onDigit: (Int) -> Unit,
) {
    val rows = model.size / DIGIT_COLUMNS
    Column(verticalArrangement = Arrangement.spacedBy(SudokuSizing.keyGap)) {
        (1..model.size).chunked(DIGIT_COLUMNS).forEachIndexed { row, rowDigits ->
            Row(horizontalArrangement = Arrangement.spacedBy(SudokuSizing.keyGap)) {
                rowDigits.forEachIndexed { column, digit ->
                    SudokuDigitKey(
                        digit = digit,
                        size = model.size,
                        remaining = model.remaining[digit - 1],
                        placed = placed?.get(digit - 1),
                        notesMode = model.notesMode,
                        reducedMotion = reducedMotion,
                        // The wave starts at the key nearest the notes toggle: bottom-left under
                        // the action row, top-right beside the fourth column.
                        staggerOrder = if (actions == SudokuKeypadActions.Row) {
                            (rows - 1 - row) * DIGIT_COLUMNS + column
                        } else {
                            row + DIGIT_COLUMNS - 1 - column
                        },
                        onClick = { onDigit(digit) },
                        modifier = if (digitTag == null) keyModifier else keyModifier.testTag(digitTag(digit)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EraseButton(enabled: Boolean, onErase: () -> Unit, modifier: Modifier, size: GameButtonSize = GameButtonSize.Regular) {
    GameButton(onClick = onErase, icon = PuzzleIcons.Erase, label = "Erase", modifier = modifier, enabled = enabled, size = size)
}

@Composable
private fun ClearButton(enabled: Boolean, onClear: () -> Unit, modifier: Modifier, size: GameButtonSize = GameButtonSize.Regular) {
    GameButton(
        onClick = onClear,
        icon = PuzzleIcons.Clear,
        label = "Clear",
        modifier = modifier.testTag("sudoku.clear"),
        enabled = enabled,
        size = size,
    )
}

/** The notes mode switch: the pencil outline off, filled in the brand accent on. */
@Composable
private fun NotesToggle(
    notesMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    size: GameButtonSize = GameButtonSize.Regular,
) {
    GameToggleButton(
        checked = notesMode,
        onCheckedChange = { onToggle() },
        icon = if (notesMode) PuzzleIcons.NotesOn else PuzzleIcons.Notes,
        label = "Notes",
        accent = MiniSudokuBrand.accent,
        modifier = modifier,
        enabled = enabled,
        size = size,
    )
}
