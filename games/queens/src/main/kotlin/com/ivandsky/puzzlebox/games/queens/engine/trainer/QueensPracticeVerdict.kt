package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/**
 * What [QueensPractice.judge] says of a move. Every verdict names the [rung] that was the easiest one with a
 * deduction on the board the move was made on, so the caller knows whether the trained technique was in play.
 */
sealed interface QueensPracticeVerdict {
    val rung: QueensRung

    /**
     * The move is part of [step], a deduction of the easiest rung (the one with the least evidence that holds it,
     * when several do). [remaining] is the squares of [step] the move leaves to make: a cross-out's other squares.
     */
    data class Right(val step: QueensStep.Deduction, val remaining: Set<Cell>) : QueensPracticeVerdict {
        override val rung: QueensRung get() = step.unit.rung
    }

    /** The move fits the solution but no deduction of the easiest rung makes it; [step] is one the easiest rung does make. */
    data class Easier(val step: QueensStep.Deduction) : QueensPracticeVerdict {
        override val rung: QueensRung get() = step.unit.rung
    }

    /** The move breaks the solution, for [why]. */
    data class Wrong(override val rung: QueensRung, val why: Why) : QueensPracticeVerdict {
        enum class Why {
            /** A queen on a square the standing queens already rule out. */
            ATTACKED,

            /** A queen on a square the solution leaves empty, though no standing queen rules it out. */
            OFF_SOLUTION,

            /** A cross on a square the solution gives a queen. */
            ON_SOLUTION,
        }
    }
}
