package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion

/**
 * The Sudoku keypad: Notes and Erase as tonal buttons above a single row of digit keys
 * 1..N. It fills the caller's width; key sizing is the caller's layout, not SudokuSizing.
 */
@Composable
fun SudokuKeypad(
    model: SudokuKeypadModel,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onToggleNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            NotesToggle(
                notesMode = model.notesMode,
                onToggle = onToggleNotes,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onErase,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 48.dp),
            ) {
                Text("Erase")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            model.remaining.forEachIndexed { index, remaining ->
                val digit = index + 1
                SudokuDigitKey(
                    digit = digit,
                    remaining = remaining,
                    notesMode = model.notesMode,
                    reducedMotion = reducedMotion,
                    onClick = { onDigit(digit) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Tonal like Erase, but a toggle: Material's tonal button only exposes a Button role,
 * and TalkBack needs a Switch with its on/off state (A14).
 */
@Composable
private fun NotesToggle(
    notesMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (notesMode) scheme.primary else scheme.secondaryContainer
    val content = if (notesMode) scheme.onPrimary else scheme.onSecondaryContainer
    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clip(ButtonDefaults.shape)
            .background(container)
            .toggleable(value = notesMode, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { stateDescription = if (notesMode) "On" else "Off" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Notes",
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}
