package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession

private fun digits(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }

private val miniPuzzle = SudokuPuzzle(
    SudokuGames.MINI.variant,
    digits("..3.5..2.4.61..........22.4.1..1.3.."),
    digits("463251521436142563356142234615615324"),
)

private val classicPuzzle = SudokuPuzzle(
    SudokuGames.CLASSIC.variant,
    digits("53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"),
    digits("534678912672195348198342567859761423426853791713924856961537284287419635345286179"),
)

/** Row 1: a wrong 3 beside the given 3 (a conflict), notes 1 4 6 next to it; the entry is selected. */
private fun miniNotesConflictSession(): SudokuSession {
    val start = SudokuSession(ref = null, puzzle = miniPuzzle)
    val board = start.board
        .with(0, SudokuCell.Entry(3))
        .with(1, SudokuCell.Notes(0b101001))
        .with(8, SudokuCell.Notes(0b110001))
    return start.copy(board = board)
}

private val previewModifier = Modifier.fillMaxWidth().padding(Spacing.md)

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun SudokuGridMiniStartPreview() {
    PuzzleBoxTheme {
        SudokuGrid(sudokuBoardModel(SudokuSession(ref = null, puzzle = miniPuzzle), selected = null), onSelect = {}, modifier = previewModifier)
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun SudokuGridMiniNotesConflictPreview() {
    PuzzleBoxTheme {
        SudokuGrid(sudokuBoardModel(miniNotesConflictSession(), selected = 0), onSelect = {}, modifier = previewModifier)
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun SudokuGridClassicStartPreview() {
    PuzzleBoxTheme {
        SudokuGrid(sudokuBoardModel(SudokuSession(ref = null, puzzle = classicPuzzle), selected = null), onSelect = {}, modifier = previewModifier)
    }
}
