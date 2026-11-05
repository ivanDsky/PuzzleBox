package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp

/**
 * Computed dp sizes for one Sudoku board ([SudokuSizing.layout]): [board] is exactly
 * size × [cell]; [key] is the keypad key height and [keyGap] the spacing between keys,
 * between keypad rows, and between the grid and the keypad. [keyRows] is how many rows
 * the digits wrap into (1 or 2) so every digit key is at least 48dp wide. [actionRows]
 * counts the button rows above the digits: Notes/Erase/Clear, plus Undo/Redo/Hint when
 * the board carries the play controls (TwoPane).
 */
data class SudokuLayout(val cell: Dp, val board: Dp, val key: Dp, val keyGap: Dp, val keyRows: Int, val actionRows: Int = 1)
