package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * How [SudokuPractice.judge] reads one move. [rung] is the easiest rung of the ladder on the board before the move
 * (null when no rung applies there); the move was on the trained technique's turn exactly when [rung] is that technique.
 */
sealed interface SudokuPracticeJudgement {

    val rung: TechniqueId?

    /** The move is part of [step], a deduction of the easiest rung (the cheapest one that makes it). */
    data class Right(override val rung: TechniqueId?, val step: SudokuStep) : SudokuPracticeJudgement

    /** The move fits the solution but no deduction of the easiest [rung] makes it; [step] is the first one that rung does hold. */
    data class Easier(override val rung: TechniqueId, val step: SudokuStep) : SudokuPracticeJudgement

    /** The move breaks the solution, or is no move a practice takes. */
    data class Wrong(override val rung: TechniqueId?, val reason: Reason) : SudokuPracticeJudgement

    /** Why a move is [Wrong]. */
    enum class Reason {

        /** A digit the solution does not hold went on a cell. */
        WrongDigit,

        /** The note of the cell's own solution digit was taken off. */
        SolutionNote,

        /** Not a placement and not a note coming off: a note put back, an erase, a clear, a digit over a digit. */
        NotAMove,
    }
}
