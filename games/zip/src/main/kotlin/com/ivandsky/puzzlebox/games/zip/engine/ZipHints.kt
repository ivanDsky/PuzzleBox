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
 * Smallest-safe-help selector over solver output (F2 §5 §3): a hint never gives the whole
 * path. Solvable trace -> Step(next cell). Provably dead trace -> Backtrack(longest
 * still-solvable prefix). Reads the solver, never the stored `solution` — works for
 * Phase-2 puzzles and post-backtrack states alike. Pure: [hint] never mutates state.
 */
class ZipHints(private val solver: ZipSolver = ZipSolver()) {

    fun hint(state: ZipState): ZipHint {
        val puzzle = state.puzzle
        val path = state.path
        return if (solver.canComplete(puzzle, path)) {
            val completion = requireNotNull(solver.completionFrom(puzzle, path))
            ZipHint.Step(completion[path.size])
        } else {
            val keep = solver.longestSolvablePrefix(puzzle, path)
            ZipHint.Backtrack(keep - 1) // index of the last cell KEPT; -1 = empty trace
        }
    }
}
