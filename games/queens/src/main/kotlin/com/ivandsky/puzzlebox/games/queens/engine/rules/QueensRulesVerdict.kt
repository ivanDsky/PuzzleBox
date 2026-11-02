package com.ivandsky.puzzlebox.games.queens.engine.rules

/**
 * Typed aggregate outcome of evaluating a placement against the full rule pipeline
 * (pinned NAME: `QueensRulesVerdict` — queens-04/05/08 consume this shape).
 *
 * - [Valid]: the placement IS the puzzle's solution — exactly
 *   [QueensPuzzle.queensPerUnit] queens per row/column/region, no touching, no extras.
 * - [Incomplete]: zero violations so far but the required queen count is not yet placed
 *   (the normal mid-play state).
 * - [Invalid]: at least one rule violation; [violations] carries every offending cell
 *   with its rule id for the Auto-check conflict highlight (A14).
 */
sealed interface QueensRulesVerdict {

    /** A finished, rule-satisfying board. No payload — validity is the information. */
    data object Valid : QueensRulesVerdict

    /** No violation, but [placed] < [required] ([QueensPuzzle.size] × queensPerUnit). */
    data class Incomplete(val placed: Int, val required: Int) : QueensRulesVerdict

    /** Rule broken. Every offending (rule, cell) pair is attributed — never just the first. */
    data class Invalid(val violations: List<QueensRuleViolation>) : QueensRulesVerdict
}
