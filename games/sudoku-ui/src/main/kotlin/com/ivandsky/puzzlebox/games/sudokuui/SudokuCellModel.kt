package com.ivandsky.puzzlebox.games.sudokuui

/**
 * What one cell draws. [digit] is 0 when the cell shows none; [notes] is the visible notes mask
 * (bit `d - 1` for digit `d`), 0 under an entry because an entry hides its notes. [hintStruck]
 * marks a cell whose candidates the hint's eliminations cleared.
 */
data class SudokuCellModel(
    val idx: Int,
    val digit: Int,
    val notes: Int,
    val given: Boolean,
    val selected: Boolean,
    val peer: Boolean,
    val sameDigit: Boolean,
    val conflict: Boolean,
    val hintTarget: Boolean,
    val hintUnit: Boolean,
    val hintStruck: Boolean,
)
