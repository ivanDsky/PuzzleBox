package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.DraftCompleter
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoSearchOutcome
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import kotlin.random.Random

/**
 * Tango's Finish for me (spec E1): adds givens to a draft until the board has one answer the
 * hint ladder can reach. Every given and marker the author placed stays; only givens are added.
 *
 * [TangoPinnedFill] draws one random answer that agrees with the pieces, and every added given
 * is read off it. Givens go first on a cell where two answers of the board differ, until one
 * answer is left; then on a cell the hint ladder leaves empty, until the ladder fills the
 * board. So what is returned is what [TangoDraftAnalyzer] calls Unique and not needs-guessing.
 * One draw always ends in a board: the answer itself, given whole, is one.
 */
object TangoCompleter : DraftCompleter<TangoDraft> {
    /** [FinishReadiness.NeedsMore] reason; IncompleteReason.count = pieces the author still has to place. */
    const val PIECES: String = "tango.finish.pieces"

    /**
     * Givens plus markers a draft must hold before Finish for me is offered, at every size.
     * Measured by `TangoCompleterMeasurement`: every sampled draft finished in milliseconds at
     * every size whatever it held, a blank board included, so the count is not a success
     * threshold. One piece is what keeps a blank board from being finished (E1: the tool starts
     * disabled and completes the author's pieces).
     */
    const val MIN_PIECES: Int = 1

    /** Blocked on givens that break a rule ([TangoClashLocator.conflicts]); NeedsMore below [MIN_PIECES]. */
    override fun readiness(draft: TangoDraft): FinishReadiness {
        val clashing = TangoClashLocator.conflicts(puzzle(draft, draft.givens))
        if (clashing.isNotEmpty()) return FinishReadiness.Blocked(clashing)
        val missing = MIN_PIECES - draft.givens.size - draft.markers.size
        return if (missing > 0) FinishReadiness.NeedsMore(IncompleteReason(PIECES, missing)) else FinishReadiness.Ready
    }

    /**
     * GaveUp when no grid holds the pieces, which includes givens that break a rule
     * ([TangoPinnedFill] refuses them before it searches), or once [budget] is spent. [target] is not
     * aimed for yet: every board is "any" until Tango has its scored grader (93).
     */
    override fun complete(draft: TangoDraft, random: Random, budget: Budget, target: Difficulty.Level?): Completion<TangoDraft> {
        val answer = TangoPinnedFill.fill(puzzle(draft, draft.givens), random, budget) ?: return Completion.GaveUp
        val givens = draft.givens.toMutableMap()
        while (true) {
            val found = TangoUniqueness.bounded(puzzle(draft, givens), limit = 2, budget = budget)
            if (found !is TangoSearchOutcome.Done) return Completion.GaveUp
            if (found.solutions.size < 2) break
            val (one, other) = found.solutions
            val cell = one.indices.filter { one[it] != other[it] }.random(random)
            givens[cell] = answer[cell]
        }
        while (true) {
            val left = leftByLadder(puzzle(draft, givens), budget) ?: return Completion.GaveUp
            if (left.isEmpty()) return Completion.Done(draft.copy(givens = givens))
            val cell = left.random(random)
            givens[cell] = answer[cell]
        }
    }

    /**
     * The cells the hint ladder leaves empty on [board]: [TangoGrader]'s walk from the givens,
     * with [budget] polled before each deduction. Null once the budget is spent.
     */
    private fun leftByLadder(board: TangoPuzzle, budget: Budget): List<Int>? {
        val grid = MutableList<TangoCell?>(board.cellCount) { board.givens[it] }
        var empty = grid.count { it == null }
        while (empty > 0) {
            if (budget.exhausted()) return null
            val hint = TangoRules.hint(TangoState(board, grid)) as? TangoHint.Forced ?: break
            grid[hint.idx] = hint.value
            empty--
        }
        return grid.indices.filter { grid[it] == null }
    }

    private fun puzzle(draft: TangoDraft, givens: Map<Int, TangoCell>): TangoPuzzle =
        TangoPuzzle(TangoDraft.variantOf(draft.n), draft.n, givens, draft.markers)
}
