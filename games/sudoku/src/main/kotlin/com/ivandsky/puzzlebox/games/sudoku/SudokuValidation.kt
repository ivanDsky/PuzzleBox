package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.MoveVerdict

/**
 * Typed move verdicts. Rule conflicts are never a rejection (spec A7): a digit that clashes
 * with a peer is still [Valid] and is shown as a conflict instead.
 */
sealed interface SudokuValidation : MoveVerdict {

    data object Valid : SudokuValidation

    /** The move targets a given cell, which never changes. */
    data object GivenCell : SudokuValidation

    /** The cell index or digit is outside the board, or the move belongs to another game. */
    data object OutOfRange : SudokuValidation

    /** The board is already solved; the win is final. */
    data object Complete : SudokuValidation
}
