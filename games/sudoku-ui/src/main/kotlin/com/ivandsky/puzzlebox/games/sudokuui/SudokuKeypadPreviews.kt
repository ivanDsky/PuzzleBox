package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

private val SIX_REMAINING = listOf(4, 3, 5, 2, 4, 3)
private val NINE_REMAINING = listOf(6, 5, 7, 4, 6, 5, 3, 6, 5)

// Board panes from prototype 01 D: the keypad is sized for the pane as the play screen sizes it.
private val SmallPhonePane = 336.dp to 386.dp
private val TallPhonePane = 388.dp to 661.dp
private val TabletTwoPane = 769.dp to 776.dp

private fun paneLayout(size: Int, pane: Pair<Dp, Dp>, inlineNotes: Boolean = false): SudokuLayout =
    SudokuSizing.layout(pane.first, pane.second, size, inlineNotes)

@PuzzleBoxPreviews
@Preview(name = "Tall phone", widthDp = 420, showBackground = true)
@Composable
internal fun SudokuKeypadSixPreview() {
    val layout = paneLayout(6, TallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = SIX_REMAINING, notesMode = false, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tall phone", widthDp = 420, showBackground = true)
@Composable
internal fun SudokuKeypadSixNotesPreview() {
    val layout = paneLayout(6, TallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = SIX_REMAINING, notesMode = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tall phone", widthDp = 420, showBackground = true)
@Composable
internal fun SudokuKeypadNinePreview() {
    val layout = paneLayout(9, TallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = false, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tall phone", widthDp = 420, showBackground = true)
@Composable
internal fun SudokuKeypadNineNotesPreview() {
    val layout = paneLayout(9, TallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Small phone", widthDp = 368, showBackground = true)
@Composable
internal fun SudokuKeypadSixActionColumnPreview() {
    val layout = paneLayout(6, SmallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = SIX_REMAINING, notesMode = false, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Small phone", widthDp = 368, showBackground = true)
@Composable
internal fun SudokuKeypadNineActionColumnPreview() {
    val layout = paneLayout(9, SmallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = false, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tall phone", widthDp = 420, showBackground = true)
@Composable
internal fun SudokuKeypadDigitDonePreview() {
    val layout = paneLayout(6, TallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = listOf(4, 0, 5, 2, 0, 3), notesMode = false, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tall phone", widthDp = 420, showBackground = true)
@Composable
internal fun SudokuKeypadSolvedPreview() {
    val layout = paneLayout(6, TallPhonePane)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = List(6) { 0 }, notesMode = false, solved = true),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tablet board pane", widthDp = 584, showBackground = true)
@Composable
internal fun SudokuKeypadNineInlinePreview() {
    val layout = paneLayout(9, TabletTwoPane, inlineNotes = true)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = false, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tablet board pane", widthDp = 584, showBackground = true)
@Composable
internal fun SudokuKeypadNineInlineNotesPreview() {
    val layout = paneLayout(9, TabletTwoPane, inlineNotes = true)
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            keyHeight = layout.key,
            actionHeight = layout.actionKey,
            actions = layout.actions,
        )
    }
}
