package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/**
 * One deduction of a hint-ladder [rung], whole: the squares it settles, one per target square (the engine's
 * [TangoStep.Deduction]s sharing rung, evidence and line). A [rung] of null is a reveal: one square no rung explains.
 */
data class TangoPracticeStep(val rung: TangoStepRung?, val squares: List<TangoPracticeSquare>) {

    init {
        require(squares.isNotEmpty()) { "a step settles at least one square" }
    }

    /** The cells the step's proof reads; empty for a reveal. */
    internal val evidenceSize: Int = (squares.first().shown as? TangoStep.Deduction)?.evidence?.size ?: 0

    /** The square at [idx], null when the step does not settle it. */
    fun squareAt(idx: Int): TangoPracticeSquare? = squares.firstOrNull { it.idx == idx }

    /** Whether the step settles [idx] as [value]. */
    fun contains(idx: Int, value: TangoCell): Boolean = squareAt(idx)?.value == value
}
