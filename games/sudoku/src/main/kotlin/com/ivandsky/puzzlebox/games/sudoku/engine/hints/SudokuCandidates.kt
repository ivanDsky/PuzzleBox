package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle

/**
 * The engine's own candidate masks. They come from the givens and the correct entries only:
 * the player's notes are never read, so a hint stays sound even when the notes are wrong.
 */
internal object SudokuCandidates {

    /** A mask per cell: every digit no known peer holds, or 0 for a cell whose digit is known. */
    fun of(puzzle: SudokuPuzzle, board: SudokuBoard): IntArray {
        val geometry = puzzle.geometry
        val known = IntArray(geometry.cellCount) { idx -> knownDigit(puzzle, board.cells[idx], idx) }
        val full = (1 shl geometry.size) - 1
        return IntArray(geometry.cellCount) { idx ->
            if (known[idx] != 0) {
                0
            } else {
                var seen = 0
                for (peer in geometry.peersOf(idx)) {
                    val digit = known[peer]
                    if (digit != 0) seen = seen or (1 shl (digit - 1))
                }
                full and seen.inv()
            }
        }
    }

    private fun knownDigit(puzzle: SudokuPuzzle, cell: SudokuCell, idx: Int): Int = when (cell) {
        is SudokuCell.Given -> cell.digit
        is SudokuCell.Entry -> if (cell.digit == puzzle.solution[idx]) cell.digit else 0
        is SudokuCell.Notes, SudokuCell.Empty -> 0
    }
}
