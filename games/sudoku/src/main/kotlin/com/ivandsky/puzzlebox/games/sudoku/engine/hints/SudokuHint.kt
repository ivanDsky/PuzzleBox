package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflict

/** What the hint button shows next, in ladder order: conflict, wrong entries, a technique step, a reveal. */
sealed interface SudokuHint : Hint {

    data class Conflict(val conflict: SudokuConflict) : SudokuHint

    /** Entry cells whose digit differs from the solution, ascending. */
    data class Wrong(val cells: List<Int>) : SudokuHint

    /**
     * [digit] goes in [cell] by [technique]. [unit] is the box or line that justifies a hidden
     * single and null for a naked single. [via] is the hardest elimination needed to reach the
     * single, null when singles alone were enough.
     */
    data class Place(
        val cell: Int,
        val digit: Int,
        val technique: SudokuTechnique,
        val unit: SudokuUnit?,
        val via: SudokuTechnique?,
    ) : SudokuHint

    /** No technique applies: the solution's digit for one open cell. */
    data class Reveal(val cell: Int, val digit: Int) : SudokuHint

    /** The board is solved. */
    data object None : SudokuHint
}
