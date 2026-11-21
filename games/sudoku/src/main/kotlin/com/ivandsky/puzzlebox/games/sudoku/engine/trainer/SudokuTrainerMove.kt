package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.notesMask

/**
 * What one player gesture did to a practice board, read off the boards either side of it ([between]). The practice
 * judges the board change, not the gesture, so the real play board's own input routing stays the only input path.
 */
sealed interface SudokuTrainerMove {

    /** [digit] went onto the open cell [idx]. */
    data class Place(val idx: Int, val digit: Int) : SudokuTrainerMove

    /** The note for [digit] came off the open cell [idx]. */
    data class RemoveNote(val idx: Int, val digit: Int) : SudokuTrainerMove

    /** The note for [digit] went onto the open cell [idx]. */
    data class AddNote(val idx: Int, val digit: Int) : SudokuTrainerMove

    /** Any other change: an erase, a clear, an undo, a digit over a digit, or several cells at once. */
    data object Other : SudokuTrainerMove

    companion object {

        /**
         * The move that turned [before] into [after]. Placing a digit also clears its note from the peers; that rides
         * along with the [Place]. Boards that differ in nothing the player can do (or in the same way twice over) are [Other].
         */
        fun between(before: SudokuBoard, after: SudokuBoard): SudokuTrainerMove {
            val cells = before.cells.indices
            val digitChanged = cells.filter { before.digitAt(it) != after.digitAt(it) }
            if (digitChanged.isNotEmpty()) {
                val idx = digitChanged.singleOrNull() ?: return Other
                return if (before.digitAt(idx) == 0) Place(idx, after.digitAt(idx)) else Other
            }
            val noteChanged = cells.filter { before.cells[it].notesMask != after.cells[it].notesMask }
            val idx = noteChanged.singleOrNull() ?: return Other
            val flipped = before.cells[idx].notesMask xor after.cells[idx].notesMask
            if (Integer.bitCount(flipped) != 1) return Other
            val digit = Integer.numberOfTrailingZeros(flipped) + 1
            return if (before.cells[idx].notesMask and flipped != 0) RemoveNote(idx, digit) else AddNote(idx, digit)
        }
    }
}
