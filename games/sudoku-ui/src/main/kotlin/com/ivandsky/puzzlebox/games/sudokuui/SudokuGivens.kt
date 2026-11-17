package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession

/**
 * [puzzle] as a session nobody has played: its given digits. The solved grid is left behind here, before a
 * session exists: nothing that draws or speaks the session can give it away. A session cannot be made without
 * a full grid of digits, so each blank is filled with the same [FILLER], which says nothing about the answer.
 * Null for another game's puzzle, for one Sudoku's board under the other's name, and for a payload that does
 * not decode.
 */
internal fun sudokuGivensSession(puzzle: Puzzle): SudokuSession? {
    val game = listOf(SudokuGames.MINI, SudokuGames.CLASSIC).firstOrNull { it.id == puzzle.gameId } ?: return null
    val decoded = game.decodePuzzle(puzzle.payload).getOrNull() ?: return null
    val givens = SudokuPuzzle(decoded.variant, decoded.givens, decoded.givens.map { if (it == 0) FILLER else it })
    return SudokuSession(ref = null, puzzle = givens)
}

private const val FILLER = 1
