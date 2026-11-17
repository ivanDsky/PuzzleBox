package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard

/**
 * Sudoku's board before play as a puzzle's page shows it, light and dark: hidden, and plain for whoever may see
 * it. The puzzle travels with its solved grid, as a shared one does; [SudokuReplayBoard.givens] leaves it behind.
 */
private val previewEnvelope: Puzzle by lazy {
    val puzzle = SudokuSamples.classic
    Puzzle(SudokuGames.CLASSIC.id, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
}

@PuzzleBoxPreviews
@Composable
internal fun SudokuHiddenBoardBlurredPreview() = HiddenBoardPane(sharp = false)

@PuzzleBoxPreviews
@Composable
internal fun SudokuHiddenBoardSharpPreview() = HiddenBoardPane(sharp = true)

@Composable
private fun HiddenBoardPane(sharp: Boolean) {
    val board = SudokuReplayBoard.givens(previewEnvelope) ?: return
    PuzzleBoxTheme { Surface { HiddenBoard(board, sharp = sharp, modifier = Modifier.padding(Spacing.lg)) } }
}
