package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

/**
 * The one move a Mini Sudoku snippet round takes (spec §2.2). The snippet board shows every candidate
 * as a note (the engine's own, not the player's), so a move is a placement or the strike of one shown note.
 */
sealed interface SudokuTrainerMove {

    /** Put [digit] on the empty cell [idx]. */
    data class Place(val idx: Int, val digit: Int) : SudokuTrainerMove

    /** Strike the shown candidate [digit] from the empty cell [idx]. */
    data class RemoveNote(val idx: Int, val digit: Int) : SudokuTrainerMove
}
