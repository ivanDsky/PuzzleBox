package com.ivandsky.puzzlebox.games.sudoku.editor

/**
 * What the hint ladder made of a board: the [hardest] rung any step before it stopped needed
 * (null when no cell was open), and whether it had to [needsGuessing] reveal a cell.
 */
data class SudokuGrade(val hardest: SudokuRung?, val needsGuessing: Boolean)
