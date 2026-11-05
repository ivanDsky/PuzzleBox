package com.ivandsky.puzzlebox.games.sudoku

/** One cell. A notes mask uses bit `d - 1` for digit `d`. */
sealed interface SudokuCell {

    data class Given(val digit: Int) : SudokuCell {
        init {
            require(digit >= 1) { "a given needs a digit, got $digit" }
        }
    }

    /** [notes] stay hidden under the entry so erasing it brings them back. */
    data class Entry(val digit: Int, val notes: Int = 0) : SudokuCell {
        init {
            require(digit >= 1) { "an entry needs a digit, got $digit" }
        }
    }

    data class Notes(val mask: Int) : SudokuCell {
        init {
            require(mask != 0) { "an empty notes mask is SudokuCell.Empty" }
        }
    }

    data object Empty : SudokuCell
}

/** The digit shown in the cell, 0 when none. */
val SudokuCell.digit: Int
    get() = when (this) {
        is SudokuCell.Given -> digit
        is SudokuCell.Entry -> digit
        is SudokuCell.Notes, SudokuCell.Empty -> 0
    }

/** The cell's notes, visible or hidden under an entry. */
internal val SudokuCell.notesMask: Int
    get() = when (this) {
        is SudokuCell.Notes -> mask
        is SudokuCell.Entry -> notes
        is SudokuCell.Given, SudokuCell.Empty -> 0
    }
