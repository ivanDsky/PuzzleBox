package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp

/**
 * Computed dp sizes for one Sudoku board ([SudokuSizing.layout]): [board] is exactly
 * size × [cell]; [key] is the keypad key height and [keyGap] the spacing between keys,
 * between the keypad's two rows, and between the grid and the keypad.
 */
data class SudokuLayout(val cell: Dp, val board: Dp, val key: Dp, val keyGap: Dp)
