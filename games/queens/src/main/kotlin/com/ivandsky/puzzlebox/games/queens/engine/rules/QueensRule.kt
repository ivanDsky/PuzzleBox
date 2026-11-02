package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * One self-contained rule of the Queens placement grammar (spec A1, research 01): a
 * predicate over one candidate placement against the already-placed queens, plus a
 * verbose whole-board scan derived from the same truth. A new rule is a new class plus
 * one line in [QueensRules.pipeline] — the zip ZipRule architecture, adapted from a
 * move trace to a queen placement set.
 *
 * Rule semantics are parameterized by [QueensPuzzle.queensPerUnit] (A9): N=1 is the
 * official daily; N≥2 (Star Battle) is the same predicate with a larger allowance —
 * never a separate code path. Adjacency detection is N-independent (research 01 §2).
 */
interface QueensRule {
    val id: QueensRuleId

    /**
     * Hot path: allocation-free. Would placing [cell] (which is not yet in [queens])
     * break this rule? The single source of truth — the rules facade, the session's
     * live placement gate, and any consumer read this shape, so the verbose [violations]
     * scan cannot diverge.
     */
    fun violated(cell: Cell, queens: Set<Cell>, puzzle: QueensPuzzle): Boolean

    /**
     * Verbose scan: every ALREADY-PLACED queen that breaks this rule on [puzzle], with
     * correct cell attribution. Derived; allocates only on failure. A queen "breaks" a
     * unit-count rule when its unit holds more than [QueensPuzzle.queensPerUnit] queens.
     */
    fun violations(queens: Set<Cell>, puzzle: QueensPuzzle): List<QueensRuleViolation>
}

/** A detected rule violation: which rule, on which queen cell. */
data class QueensRuleViolation(
    val rule: QueensRuleId,
    val cell: Cell,
)
