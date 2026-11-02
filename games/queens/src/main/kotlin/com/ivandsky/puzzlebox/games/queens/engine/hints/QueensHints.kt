package com.ivandsky.puzzlebox.games.queens.engine.hints

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver

/**
 * Game-authored hint presentation (core `Hint` marker), official-app behavior (research 03):
 * highlight a region where a crown must be placed, or indicate incorrect placements.
 * A5 ruling: payloads carry ONLY region ids and cell coordinates — the decoded
 * `solution` is consumed internally and never surfaces.
 */
sealed interface QueensHint : Hint {

    /** Highlight [region]: a crown must go at [cell] (the forced position in that region). */
    data class Region(val region: Int, val cell: Cell) : QueensHint

    /** The crowns at [cells] cannot all stay — rule-violating, or provably off the
     *  unique solution. Remove at least one; the caller re-hints after the fix. */
    data class Incorrect(val cells: List<Cell>) : QueensHint

    /** The board is already complete and correct — nothing to hint. */
    data object None : QueensHint
}

/**
 * Queens hint selector (zip precedent: stored-solution-first, solver-fallback
 * contract shape). Smallest-safe-help: one region per hint, never the rest of the board.
 *
 * Selection order:
 * 1. Rule violations among the placed crowns ([QueensRulesVerdict.Invalid]) — the
 *    offending cells, attributed (A14), as [QueensHint.Incorrect].
 * 2. Placed crowns off the stored `solution` — provably incorrect ([QueensHint.Incorrect]).
 * 3. Forced-position deduction over the solver facade ([QueensSolver.solutionsUpTo],
 *    limit 2 — never a duplicated search): the completions consistent with the placed
 *    crowns; a cell common to ALL of them is logically forced. With a unique completion
 *    every remaining cell is forced; we surface the lowest-region one as
 *    [QueensHint.Region]. With several completions and no common cell, the first
 *    completion's lowest-region cell is suggested (same smallest-safe-help posture as
 *    ZipHints' fallback step).
 * 4. No completion extends the placed crowns and no stored solution isolates the culprits:
 *    all placed cells are reported — at least one must move.
 *
 * Pure; deterministic (the solver search is deterministic).
 */
class QueensHints(private val solver: QueensSolver = QueensSolver()) {

    fun hint(puzzle: QueensPuzzle, placed: List<Cell>): QueensHint {
        // 1. Live rule violations first — attributed independently of any solution.
        val verdict = QueensRules.evaluate(puzzle, placed.toSet())
        if (verdict is QueensRulesVerdict.Invalid) {
            return QueensHint.Incorrect(
                verdict.violations.map { it.cell }.distinct().sortedBy { it.idx },
            )
        }

        val solutionIdx = puzzle.solution?.map { it.idx }?.toSet()

        // 2. Placed crowns provably off the stored solution.
        if (solutionIdx != null) {
            val misplaced = placed.filter { it.idx !in solutionIdx }
            if (misplaced.isNotEmpty()) return QueensHint.Incorrect(misplaced.sortedBy { it.idx })
        }

        // 3. Forced-position deduction via the solver facade.
        val placedIdx = placed.map { it.idx }.toSet()
        val completions = solver
            .solutionsUpTo(puzzle, limit = 2)
            .filter { sol -> placed.all { c -> sol.any { it.idx == c.idx } } }

        if (completions.isEmpty()) {
            // 4. Dead placement: with a stored solution this is unreachable (step 2
            //    already returned); blind boards report every crown as unkeepable.
            return QueensHint.Incorrect(placed.sortedBy { it.idx })
        }

        val remaining = completions.first().filter { it.idx !in placedIdx }
        if (remaining.isEmpty()) return QueensHint.None // already solved

        val forced = if (completions.size == 1) {
            remaining // unique completion: every remaining cell is forced
        } else {
            // common to ALL completions = logically forced; else suggest.
            val common = completions
                .map { sol -> sol.map { it.idx }.toSet() }
                .reduce { a, b -> a intersect b }
                .filter { it !in placedIdx }
                .map { Cell(it) }
            if (common.isNotEmpty()) common else remaining
        }

        // Lowest region id wins: the region highlighted first is stable across calls.
        val cell = forced.minBy { puzzle.regionOf(it) * puzzle.cellCount + it.idx }
        return QueensHint.Region(puzzle.regionOf(cell), cell)
    }
}
