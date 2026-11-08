package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts

/**
 * Keypad state: [remaining] holds, for digit `d`, how many more `d` the board needs at index `d - 1`.
 * A [solved] board turns Notes and Erase off; Clear is never gated (it is a no-op there).
 */
data class SudokuKeypadModel(
    val size: Int,
    val remaining: List<Int>,
    val notesMode: Boolean,
    val solved: Boolean,
)

fun sudokuKeypadModel(board: SudokuBoard, notesMode: Boolean): SudokuKeypadModel {
    val size = board.geometry.size
    val placed = IntArray(size + 1)
    board.cells.forEach { placed[it.digit]++ }
    // A conflicting board can hold more than N of a digit; the key still reads 0 left.
    val remaining = (1..size).map { (size - placed[it]).coerceAtLeast(0) }
    return SudokuKeypadModel(size, remaining, notesMode, solved = SudokuConflicts.isSolved(board))
}
