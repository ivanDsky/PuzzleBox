package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * Queens rules facade (zip ZipRules precedent, A18 architecture): thin composition over
 * the ordered [pipeline]. Each rule is a self-contained class; this object composes them
 * (cheap counts first), maps the aggregate to the typed [QueensRulesVerdict], and exposes
 * the live placement gate. Stateless singleton — pure, never mutates placement.
 *
 * Parameterization (A9): every rule reads [QueensPuzzle.queensPerUnit]; N=1 is the
 * official daily, N≥2 is the same pipeline. Board size is never special-cased.
 */
object QueensRules {

    /** The fixed rule pipeline: a new rule is a new class + one line here. */
    val pipeline: List<QueensRule> = listOf(
        RowExclusivityRule(),
        ColumnExclusivityRule(),
        RegionExclusivityRule(),
        NoTouchAdjacencyRule(),
    )

    /**
     * Live placement gate (hot path, allocation-free): would placing [cell] — currently
     * unplaced — break any rule against [queens]? The FIRST violated rule id (pipeline
     * order) feeds the rejection cue; null means the placement is legal.
     */
    fun placementViolatedBy(cell: Cell, queens: Set<Cell>, puzzle: QueensPuzzle): QueensRuleId? {
        for (rule in pipeline) {
            if (rule.violated(cell, queens, puzzle)) return rule.id
        }
        return null
    }

    /**
     * Full evaluation of [queens] against [puzzle] — the composition seam queens-03's
     * solver/generator and the A13 uniqueness gate share with the session. Invalid
     * carries EVERY violation with cell attribution (never just the first).
     */
    fun evaluate(puzzle: QueensPuzzle, queens: Set<Cell>): QueensRulesVerdict {
        val required = puzzle.size * puzzle.queensPerUnit
        val violations = buildList {
            for (rule in pipeline) addAll(rule.violations(queens, puzzle))
        }
        if (violations.isNotEmpty()) return QueensRulesVerdict.Invalid(violations)
        return if (queens.size == required) {
            QueensRulesVerdict.Valid
        } else {
            QueensRulesVerdict.Incomplete(placed = queens.size, required = required)
        }
    }

    /** Convenience: one rule's verbose scan (per-rule detail for a focused consumer). */
    fun violationsOf(ruleId: QueensRuleId, queens: Set<Cell>, puzzle: QueensPuzzle): List<QueensRuleViolation> =
        pipeline.first { it.id == ruleId }.violations(queens, puzzle)
}
