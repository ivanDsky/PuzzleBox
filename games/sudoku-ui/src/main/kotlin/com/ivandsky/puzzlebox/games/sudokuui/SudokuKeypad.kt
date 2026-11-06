package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * The Sudoku keypad: Notes, Erase and Clear as three equal tonal buttons above the digit keys
 * 1..N in [rows] rows (SudokuLayout.keyRows). Every digit key gets the same width, so a short
 * last row (9 keys in 2 rows: 5 + 4) sits centred under the full one. It fills the caller's
 * width. Every row is [keyHeight] tall (SudokuLayout.key), never under the 48dp touch target.
 * Clear is one undoable command, so it needs no confirm; a solved board turns the row off.
 *
 * [inlineNotes] (TwoPane, whose side pane holds Erase and Clear) drops the button row: the
 * notes toggle becomes one more key, a pencil, after the last digit. Switching notes morphs
 * the digit keys one by one outward from the toggle (SudokuDigitKey).
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
    inlineNotes: Boolean = false,
) {
    val rowHeight = keyHeight.coerceAtLeast(48.dp)
    val reducedMotion = rememberReducedMotion()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (!inlineNotes) {
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
        }
        // Key n of the row-major run: digits 1..N, then (inline) the notes toggle at index N.
        val keys = if (inlineNotes) model.size + 1 else model.size
        val keysPerRow = (keys + rows - 1) / rows
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val keyWidth = if (inlineNotes) {
                SudokuSizing.inlineKeyWidth(maxWidth, keysPerRow)
            } else {
                (maxWidth - SudokuSizing.digitGap * (keysPerRow - 1)) / keysPerRow
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (0 until keys).chunked(keysPerRow).forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SudokuSizing.digitGap, Alignment.CenterHorizontally),
                    ) {
                        rowKeys.forEach { key ->
                            val keyModifier = Modifier.width(keyWidth).height(rowHeight)
                            if (key == model.size) {
                                NotesKey(
                                    notesMode = model.notesMode,
                                    enabled = !model.solved,
                                    reducedMotion = reducedMotion,
                                    onToggle = onToggleNotes,
                                    modifier = Modifier.padding(start = SudokuSizing.toggleBreak - SudokuSizing.digitGap).then(keyModifier),
                                )
                            } else {
                                val digit = key + 1
                                SudokuDigitKey(
                                    digit = digit,
                                    size = model.size,
                                    remaining = model.remaining[key],
                                    notesMode = model.notesMode,
                                    reducedMotion = reducedMotion,
                                    // The wave starts at the key nearest the toggle: the last
                                    // digit beside the inline pencil, else the first, under Notes.
                                    staggerOrder = if (inlineNotes) model.size - digit else key,
                                    onClick = { onDigit(digit) },
                                    modifier = keyModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The inline notes toggle, a key like the digits: a tonal pencil off, the brand fill on.
 * A Switch to TalkBack ("Notes", On/Off) like [NotesToggle] (A14).
 */
@Composable
private fun NotesKey(
    notesMode: Boolean,
    enabled: Boolean,
    reducedMotion: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val crossfade: AnimationSpec<Color> = if (reducedMotion) snap() else tween(MotionTokens.DurationShort)
    val container by animateColorAsState(
        when {
            !enabled -> tonal.disabledContainerColor
            notesMode -> scheme.primary
            else -> scheme.secondaryContainer
        },
        crossfade,
        label = "notesKeyContainer",
    )
    val content by animateColorAsState(
        when {
            !enabled -> tonal.disabledContentColor
            notesMode -> scheme.onPrimary
            else -> scheme.onSecondaryContainer
        },
        crossfade,
        label = "notesKeyContent",
    )
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .toggleable(value = notesMode, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .semantics {
                contentDescription = "Notes"
                stateDescription = if (notesMode) "On" else "Off"
            }
            .testTag("sudoku.notes_key"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (notesMode) Icons.Filled.Edit else Icons.Outlined.Edit,
            contentDescription = null,
            tint = content,
        )
    }
}

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
