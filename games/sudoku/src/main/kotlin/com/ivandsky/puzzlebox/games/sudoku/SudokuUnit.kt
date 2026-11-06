package com.ivandsky.puzzlebox.games.sudoku

/** One row, column or box; [index] is 0-based, boxes counted row-major. */
data class SudokuUnit(val kind: UnitKind, val index: Int)
