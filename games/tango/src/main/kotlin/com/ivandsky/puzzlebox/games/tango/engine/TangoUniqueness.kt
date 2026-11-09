package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * A15: every accepted Tango puzzle has exactly one solution — ambiguous puzzles are
 * rejected, never guessed through. Exhaustive solver: forced moves and failed-literal
 * probing fill what the rules decide, then it branches on the most constrained cell; exposed for the fetch path (ticket 03) to gate dailies and
 * archive playback. JVM-only, pure.
 *
 * [bounded] polls a [Budget] at every search node and answers [TangoSearchOutcome.Exhausted]
 * once it is spent; the other entry points run the same search with [Budget.Unlimited].
 */
object TangoUniqueness {

    /**
     * True iff [puzzle] has exactly one solution under the three constraint classes.
     * Odd N is rejected outright: the count rule (n/2 per value per line) is then
     * unsatisfiable, so no solution can exist.
     */
    fun isUniquelySolvable(puzzle: TangoPuzzle): Boolean {
        if (puzzle.n % 2 != 0) return false
        return countSolutions(puzzle, limit = 2) == 1
    }

    /** Counts solutions up to [limit] by backtracking over empty cells. */
    fun countSolutions(puzzle: TangoPuzzle, limit: Int = 2): Int {
        var count = 0
        search(puzzle, limit, Budget.Unlimited) { count++ }
        return count
    }

    /** Up to [limit] distinct solutions in search order, or [TangoSearchOutcome.Exhausted] once [budget] is spent. */
    fun bounded(puzzle: TangoPuzzle, limit: Int, budget: Budget): TangoSearchOutcome {
        val solutions = ArrayList<List<TangoCell>>()
        val settled = search(puzzle, limit, budget) { grid -> solutions += grid.map { it!! } }
        return if (settled) TangoSearchOutcome.Done(solutions) else TangoSearchOutcome.Exhausted
    }

    /**
     * [puzzle]'s one solution, row-major and fully filled (givens included), by the same
     * search [countSolutions] runs; null when it has none or more than one (A15: an
     * ambiguous puzzle is never guessed through) or an odd size.
     */
    fun solve(puzzle: TangoPuzzle): List<TangoCell>? {
        if (puzzle.n % 2 != 0) return null
        var first: List<TangoCell>? = null
        var count = 0
        search(puzzle, limit = 2, Budget.Unlimited) { grid ->
            if (first == null) first = grid.map { it!! }
            count++
        }
        return first.takeIf { count == 1 }
    }

    /**
     * Depth-first search handing each full grid to [onSolution], up to [limit]. Every node fills
     * the forced cells ([TangoPropagation.propagate], then failed-literal [TangoPropagation.probe])
     * and branches SUN then MOON on [TangoPropagation.mostConstrained]. Polls [budget] once per node;
     * false when it ran out before the search settled.
     */
    private fun search(puzzle: TangoPuzzle, limit: Int, budget: Budget, onSolution: (Array<TangoCell?>) -> Unit): Boolean {
        val state = TangoPropagation(puzzle)
        var count = 0
        var exhausted = false

        fun rec() {
            if (exhausted || count >= limit) return
            if (budget.exhausted()) {
                exhausted = true
                return
            }
            if (!state.propagate() || !state.probe()) return
            val idx = state.mostConstrained()
            if (idx < 0) {
                count++
                onSolution(state.grid)
                return
            }
            for (value in branchOrder) {
                val mark = state.mark()
                if (state.place(idx, value)) rec()
                state.undoTo(mark)
            }
        }
        rec()
        return !exhausted
    }

    private val branchOrder = arrayOf(TangoCell.SUN, TangoCell.MOON)
}
