package com.ivandsky.puzzlebox.games.queens.engine.solve

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict

/**
 * Exhaustive backtracking oracle (A13): solve(), countSolutions() with early exit, and
 * the placement verification the A13 gate reads. Deterministic — the same puzzle always
 * yields the same first solution, so hints and tests are stable.
 *
 * Thin facade (zip precedent: ZipSolver over ZipSearch/ZipRules): [QueensSearch] owns the
 * DFS, queens-02's [QueensRules] composition seam owns verification; this class keeps the
 * public API (and [Result]) that the generator, the A13 decode-time gate, and the hint
 * engine read. Uniqueness claims are exact: the search is exhaustive up to the requested
 * limit (limit = 2 answers "exactly one?" in every case).
 */
class QueensSolver {

    data class Result(
        val solutionCount: Int,
        val firstSolution: List<Cell>?,
    )

    /** The unique solution, or null when none exists / the placement is inconsistent. */
    fun solve(puzzle: QueensPuzzle): List<Cell>? = search(puzzle, limit = 2).firstSolution

    /** Number of rule-satisfying placements, counted up to [limit] (A13 needs limit = 2). */
    fun countSolutions(puzzle: QueensPuzzle, limit: Int = 2): Int = search(puzzle, limit).solutionCount

    /** The exhaustive search itself: [QueensSearch]. */
    fun search(puzzle: QueensPuzzle, limit: Int): Result = QueensSearch.search(puzzle, limit)

    /** Up to [limit] distinct solutions in deterministic search order (repair loops, tests). */
    fun solutionsUpTo(puzzle: QueensPuzzle, limit: Int): List<List<Cell>> =
        QueensSearch.solutions(puzzle, limit)

    /** [solutionsUpTo] under [budget]: [QueensSearchOutcome.Exhausted] once it is spent (editor). */
    fun solutionsWithin(puzzle: QueensPuzzle, limit: Int, budget: Budget): QueensSearchOutcome =
        QueensSearch.bounded(puzzle, limit, budget)

    /**
     * Is [queens] the puzzle's rule-satisfying solution? Delegates to the queens-02
     * composition seam — [QueensRules.evaluate] — so the solver, session, and A13 gate
     * share one truth.
     */
    fun verifyPlacement(puzzle: QueensPuzzle, queens: List<Cell>): Boolean =
        QueensRules.evaluate(puzzle, queens.toSet()) is QueensRulesVerdict.Valid

    /**
     * Which rule would placing [candidate] break against [placed]? (zip precedent: the
     * facade's live gate re-export for hint/diagnostic consumers.) Null when legal.
     */
    fun placementViolatedBy(candidate: Cell, placed: List<Cell>, puzzle: QueensPuzzle): QueensRuleId? =
        QueensRules.placementViolatedBy(candidate, placed.toSet(), puzzle)
}
