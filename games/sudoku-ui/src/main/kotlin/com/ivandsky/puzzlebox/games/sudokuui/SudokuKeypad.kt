package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion

/**
 * The Sudoku keypad: Notes, Erase and Clear as three equal tonal buttons above the digit keys
 * 1..N in [rows] rows (SudokuLayout.keyRows). Every digit key gets the same width, so a short
 * last row (9 keys in 2 rows: 5 + 4) sits centred under the full one. It fills the caller's
 * width. Every row is [keyHeight] tall (SudokuLayout.key), never under the 48dp touch target.
 * Clear is one undoable command, so it needs no confirm; a solved board turns the row off.
 */
@Composable
fun SudokuKeypad(
    model: SudokuKeypadModel,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onClear: () -> Unit,
    onToggleNotes: () -> Unit,
    modifier: Modifier = Modifier,
    rows: Int = 1,
    keyHeight: Dp = 48.dp,
) {
    val rowHeight = keyHeight.coerceAtLeast(48.dp)
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
                enabled = !model.solved,
                onToggle = onToggleNotes,
                modifier = Modifier.weight(1f).height(rowHeight),
            )
            FilledTonalButton(
                onClick = onErase,
                enabled = !model.solved,
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight),
            ) {
                Text("Erase")
            }
            FilledTonalButton(
                onClick = onClear,
                enabled = model.canClear && !model.solved,
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight)
                    .testTag("sudoku.clear"),
            ) {
                Text("Clear")
            }
        }
        val keysPerRow = (model.size + rows - 1) / rows
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val keyWidth = (maxWidth - DIGIT_GAP * (keysPerRow - 1)) / keysPerRow
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                model.remaining.chunked(keysPerRow).forEachIndexed { row, rowRemaining ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DIGIT_GAP, Alignment.CenterHorizontally),
                    ) {
                        rowRemaining.forEachIndexed { column, remaining ->
                            val digit = row * keysPerRow + column + 1
                            SudokuDigitKey(
                                digit = digit,
                                remaining = remaining,
                                notesMode = model.notesMode,
                                reducedMotion = reducedMotion,
                                onClick = { onDigit(digit) },
                                modifier = Modifier.width(keyWidth).height(rowHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DIGIT_GAP = Spacing.xs

/**
 * Tonal like Erase, but a toggle: Material's tonal button only exposes a Button role,
 * and TalkBack needs a Switch with its on/off state (A14). Disabled, it takes the tonal
 * button's disabled colours.
 */
@Composable
private fun NotesToggle(
    notesMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val container = when {
        !enabled -> tonal.disabledContainerColor
        notesMode -> scheme.primary
        else -> tonal.containerColor
    }
    val content = when {
        !enabled -> tonal.disabledContentColor
        notesMode -> scheme.onPrimary
        else -> tonal.contentColor
    }
    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clip(ButtonDefaults.shape)
            .background(container)
            .toggleable(value = notesMode, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
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
