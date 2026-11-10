package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

/**
 * The deduction a player can verify BY EYE: [rung] is the ladder rung that found it and
 * [unit] the row/column/region it names — for [SinglesRung], every OTHER cell of that unit
 * is directly blocked by the standing queens, so the hinted cell must take the unit's queen.
 * Carries a unit kind + index only; no solution data ever leaves the engine (A5).
 */
data class HintReason(val kind: Kind, val unit: Int, val rung: QueensRung = SinglesRung) {
    enum class Kind { ROW, COLUMN, REGION }
}
