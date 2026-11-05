package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp

/**
 * Computed dp sizes for one Sudoku board ([SudokuSizing.layout]): [board] is exactly
 * size × [cell]; [key] is the keypad key height and [keyGap] the spacing between keys,
 * between keypad rows, and between the grid and the keypad. [keyRows] is how many rows
 * the digits wrap into (1 or 2) so every digit key is at least 48dp wide. [inlineNotes]
 * drops the Notes/Erase/Clear row above the digits for a notes toggle key after them (TwoPane).
 */
data class SudokuLayout(val cell: Dp, val board: Dp, val key: Dp, val keyGap: Dp, val keyRows: Int, val inlineNotes: Boolean = false)
