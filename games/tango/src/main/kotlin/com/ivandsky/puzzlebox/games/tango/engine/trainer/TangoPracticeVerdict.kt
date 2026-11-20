package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/** What the practice board makes of one move (spec §2.2): keep it, or undo it and say why. */
sealed interface TangoPracticeVerdict {

    /**
     * Part of a deduction of the easiest rung: [step] is that deduction, whole. [trained] is whether its rung is the
     * technique being practised; false for an easier rung and for a reveal (a square no rung explains).
     */
    data class Right(val step: TangoPracticeStep, val trained: Boolean) : TangoPracticeVerdict

    /**
     * The move fits the solution, but [rung] is the easiest rung on the board and none of its deductions makes it.
     * [step] is the one of them nearest the move.
     */
    data class Easier(val rung: TangoStepRung, val step: TangoPracticeStep) : TangoPracticeVerdict

    /**
     * The move differs from the solution. [rung] is the board's easiest rung (null when only a reveal is left): the
     * practice counts the miss when it is the technique being practised.
     */
    data class Wrong(val rung: TangoStepRung?) : TangoPracticeVerdict
}
