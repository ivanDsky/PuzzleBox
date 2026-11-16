package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique

/**
 * One kind of step in a scored solve (92), easiest first, with the [weight] one such step adds
 * to the score and the [rung] it belongs to. The four singles place a digit; the rest strike
 * candidates when no single is left, and weigh many times a single: a solver who meets one
 * has looked at the whole board in vain first.
 */
internal enum class SudokuSolveStep(val weight: Int, val rung: SudokuRung) {
    /** The only open cell of a row, column or box. */
    LAST_CELL(1, SudokuRung.SINGLES),

    /** A digit with one home left in a box. */
    BOX_SINGLE(2, SudokuRung.SINGLES),

    /** A digit with one home left in a row or column. */
    LINE_SINGLE(3, SudokuRung.SINGLES),

    /** A cell with one digit left. */
    NAKED_SINGLE(4, SudokuRung.SINGLES),
    POINTING(30, SudokuRung.INTERSECTIONS),
    CLAIMING(35, SudokuRung.INTERSECTIONS),
    NAKED_PAIR(60, SudokuRung.PAIRS),
    HIDDEN_PAIR(70, SudokuRung.PAIRS),
    NAKED_TRIPLE(100, SudokuRung.TRIPLES),
    HIDDEN_TRIPLE(120, SudokuRung.TRIPLES);

    companion object {
        /** The step an elimination [technique] counts as. */
        fun striking(technique: SudokuTechnique): SudokuSolveStep = when (technique) {
            SudokuTechnique.POINTING -> POINTING
            SudokuTechnique.CLAIMING -> CLAIMING
            SudokuTechnique.NAKED_PAIR -> NAKED_PAIR
            SudokuTechnique.HIDDEN_PAIR -> HIDDEN_PAIR
            SudokuTechnique.NAKED_TRIPLE -> NAKED_TRIPLE
            SudokuTechnique.HIDDEN_TRIPLE -> HIDDEN_TRIPLE
            SudokuTechnique.HIDDEN_SINGLE_BOX,
            SudokuTechnique.HIDDEN_SINGLE_LINE,
            SudokuTechnique.NAKED_SINGLE -> throw IllegalArgumentException("$technique places a digit, it strikes none")
        }
    }
}
