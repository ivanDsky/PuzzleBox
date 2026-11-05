package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts

/**
 * Keypad state: [remaining] holds, for digit `d`, how many more `d` the board needs at index `d - 1`.
 * [canClear] is true while anything but the givens is on the board; a [solved] board turns
 * Notes, Erase and Clear off.
 */
data class SudokuKeypadModel(
    val size: Int,
    val remaining: List<Int>,
    val notesMode: Boolean,
    val canClear: Boolean,
    val solved: Boolean,
)

fun sudokuKeypadModel(board: SudokuBoard, notesMode: Boolean): SudokuKeypadModel {
    val size = board.geometry.size
    val placed = IntArray(size + 1)
    board.cells.forEach { placed[it.digit]++ }
    // A conflicting board can hold more than N of a digit; the key still reads 0 left.
    val remaining = (1..size).map { (size - placed[it]).coerceAtLeast(0) }
    val canClear = board.cells.any { it !is SudokuCell.Given && it != SudokuCell.Empty }
    return SudokuKeypadModel(size, remaining, notesMode, canClear, solved = SudokuConflicts.isSolved(board))
}
