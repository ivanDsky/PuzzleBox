package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp

/**
 * Computed dp sizes for one Sudoku board ([SudokuSizing.layout]): [board] is exactly
 * size × [cell]; [key] is the digit key height and [keyGap] the spacing between keys,
 * between keypad rows, and between the grid and the keypad. The digits sit in three
 * columns of [keyRows] rows (size / 3). [actions] places Notes, Erase and Clear;
 * [actionKey] is their height (the action row's, or [key] beside the digits).
 */
data class SudokuLayout(
    val cell: Dp,
    val board: Dp,
    val key: Dp,
    val actionKey: Dp,
    val keyGap: Dp,
    val keyRows: Int,
    val actions: SudokuKeypadActions,
)
