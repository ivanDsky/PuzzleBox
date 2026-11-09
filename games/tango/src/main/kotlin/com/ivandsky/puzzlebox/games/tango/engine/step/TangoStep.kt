package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict

/**
 * The one step a stepwise hint shows. Carries no roles or copy; the UI maps it.
 *
 * A deduction that fixes several cells under one piece of evidence is one [Deduction] per target
 * cell, all sharing the same [Deduction.rung] and [Deduction.evidence].
 */
sealed interface TangoStep {

    /** [idx] differs from the solution; [reason] names the broken rule when one is broken. */
    data class Mistake(val idx: Int, val reason: TangoVerdict.Reason?) : TangoStep

    /**
     * [rung] proves [idx] holds [value]. [evidence] lists the cells the proof reads; a marker
     * the proof uses contributes both of its cells. [row]/[col] name the line for line-scoped
     * rungs (FULL_LINE, SIGN_COUNT, SPLIT_PAIR, COUNT_SQUEEZE, LINE_OPTIONS) and are null
     * otherwise; [options] is the number of legal line fills, non-null only for LINE_OPTIONS.
     */
    data class Deduction(
        val rung: TangoStepRung,
        val idx: Int,
        val value: TangoCell,
        val evidence: Set<Int>,
        val row: Int?,
        val col: Int?,
        val options: Int?,
    ) : TangoStep

    /** No rung fired: the solution's [value] at [idx], shown without a proof. */
    data class Reveal(val idx: Int, val value: TangoCell) : TangoStep

    data object Solved : TangoStep

    /** No sound step exists: no rung fired AND the solution is null (not computable in budget; expected unreachable on real boards). Never an unsound Reveal. */
    data object Unavailable : TangoStep
}
