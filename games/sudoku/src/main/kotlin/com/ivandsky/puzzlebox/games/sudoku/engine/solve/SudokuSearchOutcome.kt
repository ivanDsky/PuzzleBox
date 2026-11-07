package com.ivandsky.puzzlebox.games.sudoku.engine.solve

/**
 * A [SudokuSolver.bounded] run: settled with up to its limit of distinct solutions (row-major
 * digits, in search order), or stopped because the budget ran out first.
 */
sealed interface SudokuSearchOutcome {
    data class Done(val solutions: List<List<Int>>) : SudokuSearchOutcome
    data object Exhausted : SudokuSearchOutcome
}
