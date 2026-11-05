package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.Move

/** Plugin-contract moves for generic consumers; the play UI drives the session directly. */
sealed interface SudokuMove : Move {

    data class Place(val idx: Int, val digit: Int) : SudokuMove

    data class Erase(val idx: Int) : SudokuMove

    data class ToggleNote(val idx: Int, val digit: Int) : SudokuMove

    data object Clear : SudokuMove
}
