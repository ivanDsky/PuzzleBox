package com.ivandsky.puzzlebox.games.sudoku.engine.hints

/** The shipped technique ladder, easiest first; ordinal order is difficulty order. */
enum class SudokuTechnique {
    HIDDEN_SINGLE_BOX,
    HIDDEN_SINGLE_LINE,
    NAKED_SINGLE,
    POINTING,
    CLAIMING,
    NAKED_PAIR,
    HIDDEN_PAIR,
    NAKED_TRIPLE,
    HIDDEN_TRIPLE,
}
