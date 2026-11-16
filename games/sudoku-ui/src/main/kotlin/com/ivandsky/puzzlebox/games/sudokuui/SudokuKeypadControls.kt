package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.GameToggleButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * The Corner keypad's strip over the digits ([SudokuKeypadActions.Corner]): the Notes toggle in
 * its right corner, [SudokuSizing.keyMin] square so the hit area holds the 48dp floor, and
 * Erase filling the rest of the [SudokuSizing.cornerStrip], or empty space where [showErase] is
 * false (the side column has it). The toggle is corner-only: no long-press.
 */
@Composable
internal fun SudokuCornerStrip(
    notesMode: Boolean,
    editable: Boolean,
    showErase: Boolean,
    onErase: () -> Unit,
    onToggleNotes: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(SudokuSizing.cornerStrip),
        horizontalArrangement = Arrangement.spacedBy(SudokuSizing.keyGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showErase) {
            EraseButton(editable, onErase, Modifier.weight(1f).fillMaxHeight().testTag("sudoku.erase"))
        } else {
            Spacer(Modifier.weight(1f))
        }
        NotesToggle(
            notesMode,
            editable,
            onToggleNotes,
            Modifier.width(SudokuSizing.keyMin).height(SudokuSizing.keyMin).testTag("sudoku.notes_toggle"),
            GameButtonSize.Compact,
        )
    }
}

@Composable
internal fun EraseButton(enabled: Boolean, onErase: () -> Unit, modifier: Modifier, size: GameButtonSize = GameButtonSize.Regular) {
    GameButton(onClick = onErase, icon = PuzzleIcons.Erase, label = "Erase", modifier = modifier, enabled = enabled, size = size)
}

@Composable
internal fun ClearButton(onClear: () -> Unit, modifier: Modifier, size: GameButtonSize = GameButtonSize.Regular) {
    GameButton(
        onClick = onClear,
        icon = PuzzleIcons.Clear,
        label = "Clear",
        modifier = modifier.testTag("sudoku.clear"),
        size = size,
    )
}

/** The notes mode switch: the pencil outline off, filled in the brand accent on. */
@Composable
internal fun NotesToggle(
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
