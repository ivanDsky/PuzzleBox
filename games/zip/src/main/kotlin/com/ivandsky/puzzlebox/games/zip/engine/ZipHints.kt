package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.games.zip.Cell

/** Game-authored hint presentation (core `Hint` marker): reveal one cell, or trim first. */
sealed interface ZipHint : Hint {
    /** Highlight [cell] — the next cell of a solution extending the current trace. */
    data class Step(val cell: Cell) : ZipHint

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

    /** Boards without a stored solution: the solver answers as before. */
    private fun solverFallback(state: ZipState): ZipHint {
        val puzzle = state.puzzle
        val path = state.path
        return if (solver.canComplete(puzzle, path)) {
            val completion = requireNotNull(solver.completionFrom(puzzle, path))
            ZipHint.Step(completion[path.size])
        } else {
            ZipHint.Backtrack(solver.longestSolvablePrefix(puzzle, path) - 1)
        }
    }
}
