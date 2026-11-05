package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle

private val PREVIEW_SOLUTION = "463251521436142563356142234615615324".map { it.digitToInt() }
private val PREVIEW_GIVENS = "003050020406100000000002204010010300".map { it.digitToInt() }

private fun previewBoard(): SudokuBoard =
    SudokuBoard.of(SudokuPuzzle(SudokuGames.MINI.variant, PREVIEW_GIVENS, PREVIEW_SOLUTION))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 480, showBackground = true)
@Composable
internal fun SudokuPlayBoardFreshPreview() {
    PuzzleBoxTheme {
        SudokuPlayBoard(board = previewBoard(), selected = null, onSelect = {}, onDigit = {})
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 480, showBackground = true)
@Composable
internal fun SudokuPlayBoardEntrySelectedPreview() {
    PuzzleBoxTheme {
        SudokuPlayBoard(board = previewBoard().with(0, SudokuCell.Entry(4)), selected = 0, onSelect = {}, onDigit = {})
    }
}
