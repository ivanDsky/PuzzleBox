package com.ivandsky.puzzlebox.games.sudoku.engine.rules

import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit

/** [digit] appears more than once in [unit]; [cells] are those cells, ascending, Givens included. */
data class SudokuConflict(val unit: SudokuUnit, val digit: Int, val cells: List<Int>)
