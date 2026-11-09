package com.ivandsky.puzzlebox.games.sudoku.engine.hints.step

import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique

/** The one next step [SudokuStepper] finds on a board: a wrong entry, a single, an elimination, a reveal, or done. */
sealed interface SudokuStep {

    /** The entry at [cell] holds [digit], which is not the solution's; [conflictsWith] is a peer holding the same digit, if any. */
    data class Mistake(val cell: Int, val digit: Int, val conflictsWith: Int?) : SudokuStep

    /**
     * [place] is the digit the board forces, with `via == null` and empty `struck`. [lastCell] is the unit whose only
     * empty cell it fills (catalog D0), null for the other singles; a last cell's [place] carries that unit's hidden
     * single technique. [blockers] are the placed cells whose digits rule the digit out of the unit's other cells, or
     * for a naked single the cells holding the digits the target sees.
     */
    data class Single(val place: SudokuHint.Place, val lastCell: SudokuUnit?, val blockers: Set<Int>) : SudokuStep

    /**
     * [technique] in [unit] clears [struck] (cell to digit mask); [evidence] are the cells that prove it. [group] is
     * the digit mask the proof is about: the one digit of a pointing or claiming, the two digits of a naked pair,
     * the two kept digits of a hidden pair. It is the engine's own, so it holds even after earlier strikes.
     */
    data class Elimination(
        val technique: SudokuTechnique,
        val unit: SudokuUnit?,
        val group: Int,
        val struck: Map<Int, Int>,
        val evidence: Set<Int>,
    ) : SudokuStep

    /** No simple step applies: the solution's [digit] for [cell]. */
    data class Reveal(val cell: Int, val digit: Int) : SudokuStep

    /** The board is full and holds no wrong entry. */
    data object Solved : SudokuStep
}
