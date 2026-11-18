package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.games.zip.Cell

/** Game-authored hint presentation (core `Hint` marker): reveal one cell, or trim first. */
sealed interface ZipHint : Hint {
    /**
     * Highlight [cell] — the next cell of a solution extending the current trace. Under the
     * first-move rule ([ZipHints.firstMove]) [lead] holds the cells the player already knows, in
     * order from the trace head up to [cell]; the caller places them before the hint, uncounted.
     */
    data class Step(val cell: Cell, val lead: List<Cell> = emptyList()) : ZipHint

    /** The trace is provably dead: retreat to path index [trimTo] (keep path[0..trimTo];
     *  -1 = trim to empty). The trim itself is free (F2 §10) — the caller applies it and
     *  re-hints; the resulting Step is the ONE counted hint (A3). */
    data class Backtrack(val trimTo: Int) : ZipHint
}

/**
 * Smallest-safe-help selector (F2 §5 §3): a hint never gives the whole path.
 *
 * A18 addendum §6: the stored `solution` is the play-time hint source — `hint(state)`
 * walks the longest common prefix of trace and solution, then either Steps onto the next
 * correct segment ([ZipHint.Step]) or Backtracks to the last agreeing cell. Allocation-
 * free and solver-independent at play time; for uniquely-solvable boards the divergence
 * point IS the longest still-solvable prefix, so player-visible behavior is unchanged.
 * Boards without a stored solution fall back to the solver ([ZipSolver]). Pure.
 */
class ZipHints(private val solver: ZipSolver = ZipSolver()) {

    fun hint(state: ZipState): ZipHint {
        val solution = state.puzzle.solution ?: return solverFallback(state)
        val path = state.path
        var diverge = 0
        val limit = minOf(path.size, solution.size)
        while (diverge < limit && path[diverge] == solution[diverge]) diverge++
        return if (diverge == path.size && path.size < solution.size) {
            ZipHint.Step(solution[path.size]) // next correct segment
        } else {
            ZipHint.Backtrack(diverge - 1) // keep path[0..diverge-1]; -1 = empty trace
        }
    }

    /**
     * The hint under the first-move rule ([ZipFirstMovePicker]): the same trim as
     * [hint] for a dead trace, otherwise the first real choice with the known cells before it as
     * the step's lead. A stored solution is the board's only one (A16 fetch gate, generator), so
     * each of its steps is already proven and nothing is searched; a board without one asks the solver.
     */
    fun firstMove(state: ZipState): ZipHint {
        val puzzle = state.puzzle
        val path = state.path
        val stored = puzzle.solution
        if (stored == null) {
            val line = solver.completionFrom(puzzle, path)?.takeIf { it.size > path.size } ?: return hint(state)
            return searched.pick(puzzle, path, line)
        }
        val legacy = hint(state)
        return if (legacy is ZipHint.Step) unique.pick(puzzle, path, stored) else legacy
    }

    private val unique = ZipFirstMovePicker { _, _, _ -> true }

    private val searched = ZipFirstMovePicker { puzzle, prefix, others ->
        others.none { solver.canComplete(puzzle, prefix + it) }
    }

    /** Boards without a stored solution: the solver answers as before. */
    private fun solverFallback(state: ZipState): ZipHint {
        val puzzle = state.puzzle
        val path = state.path
        // ONE search answers both facts: solutionCount > 0 ⇔ canComplete, and
        // firstSolution is the completion extending the trace.
        val search = solver.search(puzzle, path, limit = 1)
        return if (search.solutionCount > 0) {
            ZipHint.Step(requireNotNull(search.firstSolution)[path.size])
        } else {
            ZipHint.Backtrack(solver.longestSolvablePrefix(puzzle, path) - 1)
        }
    }
}
