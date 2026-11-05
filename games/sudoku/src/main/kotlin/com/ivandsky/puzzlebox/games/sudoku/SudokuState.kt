package com.ivandsky.puzzlebox.games.sudoku

/** The rules state generic plugin consumers read: the puzzle plus the current board. */
data class SudokuState(val puzzle: SudokuPuzzle, val board: SudokuBoard)
