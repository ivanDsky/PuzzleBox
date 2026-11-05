package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

private val SIX_REMAINING = listOf(4, 3, 5, 2, 4, 3)
private val NINE_REMAINING = listOf(6, 5, 7, 4, 6, 5, 3, 6, 5)

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun SudokuKeypadSixPreview() {
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = SIX_REMAINING, notesMode = false, canClear = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun SudokuKeypadSixNotesPreview() {
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = SIX_REMAINING, notesMode = true, canClear = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun SudokuKeypadNinePreview() {
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = false, canClear = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun SudokuKeypadNineTwoRowsPreview() {
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = false, canClear = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
            rows = 2,
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun SudokuKeypadNineNotesPreview() {
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 9, remaining = NINE_REMAINING, notesMode = true, canClear = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun SudokuKeypadDigitDonePreview() {
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = listOf(4, 0, 5, 2, 0, 3), notesMode = false, canClear = true, solved = false),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun SudokuKeypadSolvedPreview() {
    PuzzleBoxTheme {
        SudokuKeypad(
            model = SudokuKeypadModel(size = 6, remaining = List(6) { 0 }, notesMode = false, canClear = true, solved = true),
            onDigit = {},
            onErase = {},
            onClear = {},
            onToggleNotes = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
