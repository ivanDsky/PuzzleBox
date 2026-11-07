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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * The Sudoku keypad: digit keys 1..N in three columns (6x6: 2 rows, 9x9: 3 rows), each
 * [keyHeight] tall (SudokuLayout.key), filling the caller's width. [actions] places Notes,
 * Erase and Clear (SudokuLayout.actions):
 * - [SudokuKeypadActions.Row]: three equal tonal buttons under the digits, [actionHeight] tall.
 * - [SudokuKeypadActions.Column]: a fourth column beside the digits at key height, one per
 *   row; with two digit rows (6x6) Notes takes the first and Erase | Clear split the second.
 * - [SudokuKeypadActions.NotesColumn] (TwoPane, whose side pane holds Erase and Clear): the
 *   notes toggle alone as a pencil key the digits' height in the fourth column.
 * Clear is one undoable command, so it needs no confirm; a solved board turns the actions off.
 * Switching notes morphs the digit keys one by one outward from the toggle (SudokuDigitKey).
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
) {
    val reducedMotion = rememberReducedMotion()
    val rows = model.size / DIGIT_COLUMNS
    val gap = SudokuSizing.keyGap
    val editable = !model.solved
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val keyWidth = SudokuSizing.digitKeyWidth(maxWidth, rows, actions)
        val digits: @Composable () -> Unit = {
            DigitBlock(model, actions, Modifier.width(keyWidth).height(keyHeight), reducedMotion, onDigit)
        }
        if (actions == SudokuKeypadActions.Row) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                digits()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    val action = Modifier.weight(1f).height(actionHeight)
                    NotesToggle(model.notesMode, enabled = editable, onToggle = onToggleNotes, modifier = action)
                    EraseButton(enabled = editable, onErase = onErase, modifier = action)
                    ClearButton(enabled = editable && model.canClear, onClear = onClear, modifier = action)
                }
            }
        } else {
            val columnWidth = SudokuSizing.actionColumnWidth(maxWidth, rows, actions)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                digits()
                Column(Modifier.width(columnWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (actions == SudokuKeypadActions.NotesColumn) {
                        NotesKey(
                            notesMode = model.notesMode,
                            enabled = editable,
                            reducedMotion = reducedMotion,
                            onToggle = onToggleNotes,
                            modifier = Modifier.fillMaxWidth().height(keyHeight * rows + gap * (rows - 1)),
                        )
                    } else {
                        val tile = Modifier.fillMaxWidth().height(keyHeight)
                        NotesToggle(model.notesMode, enabled = editable, onToggle = onToggleNotes, modifier = tile)
                        if (rows >= DIGIT_COLUMNS) {
                            EraseButton(enabled = editable, onErase = onErase, modifier = tile, contentPadding = TilePadding)
                            ClearButton(enabled = editable && model.canClear, onClear = onClear, modifier = tile, contentPadding = TilePadding)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                val half = Modifier.weight(1f).height(keyHeight)
                                EraseButton(enabled = editable, onErase = onErase, modifier = half, contentPadding = TilePadding)
                                ClearButton(enabled = editable && model.canClear, onClear = onClear, modifier = half, contentPadding = TilePadding)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val DIGIT_COLUMNS = 3

// Fourth-column tiles can be narrower and shorter than a tonal button's default padding allows.
private val TilePadding = PaddingValues(horizontal = Spacing.xs)

/** The digits row-major in three columns, every key [keyModifier]-sized. */
@Composable
private fun DigitBlock(
    model: SudokuKeypadModel,
    actions: SudokuKeypadActions,
    keyModifier: Modifier,
    reducedMotion: Boolean,
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
                        modifier = keyModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun EraseButton(
    enabled: Boolean,
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    FilledTonalButton(onClick = onErase, enabled = enabled, modifier = modifier, contentPadding = contentPadding) {
        Text("Erase", maxLines = 1)
    }
}

@Composable
private fun ClearButton(
    enabled: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    FilledTonalButton(
        onClick = onClear,
        enabled = enabled,
        modifier = modifier.testTag("sudoku.clear"),
        contentPadding = contentPadding,
    ) {
        Text("Clear", maxLines = 1)
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
