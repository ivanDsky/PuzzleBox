package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.DraftCompleter
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearchOutcome
import kotlin.random.Random

/**
 * Queens' Finish for me (spec E1): fills the unpainted cells of a draft so the board has one
 * answer the hint ladder can reach. Every painted cell keeps its region and every answer
 * crown stays on a queen of that answer. Each [QueensPinnedGrowth] attempt runs on its own
 * [Random] seeded from the injected one, and a board is returned only when the engine's
 * exhaustive search and the hint ladder agree with what [QueensDraftAnalyzer] would say of
 * it: Unique and not needs-guessing.
 */
object QueensCompleter : DraftCompleter<QueensDraft> {
    /** [FinishReadiness.NeedsMore] reason; IncompleteReason.count = regions the author still has to start. */
    const val STARTED: String = "queens.finish.started"

    /**
     * Regions that must hold a painted cell before Finish for me is offered, for sizes
     * [QueensVariants.MIN_SIZE]..[QueensVariants.MAX_SIZE]: `size / 2 + 1`, most of them.
     * Measured by `QueensCompleterMeasurement`: every sampled draft at this minimum finished
     * within the budget at every size, and starting more regions did not raise the rate.
     */
    private val MIN_STARTED_BY_SIZE = listOf(3, 3, 4, 4, 5, 5, 6, 6, 7)

    /**
     * Attempts per [complete], so an unbudgeted run ends. A small board spends them in a
     * fraction of a second; at sizes 11 and 12 the Studio's 2 s budget ends the run first.
     */
    private const val MAX_ATTEMPTS = 4096

    fun minimumStarted(size: Int): Int = MIN_STARTED_BY_SIZE[size - QueensVariants.MIN_SIZE]

    /** Blocked on clashing crowns ([QueensCrowns.conflicts]); NeedsMore below [minimumStarted]. */
    override fun readiness(draft: QueensDraft): FinishReadiness {
        val clashing = QueensCrowns.conflicts(draft)
        if (clashing.isNotEmpty()) return FinishReadiness.Blocked(clashing)
        val started = draft.regions.filter { it != QueensDraft.UNPAINTED }.distinct().size
        val missing = minimumStarted(draft.size) - started
        return if (missing > 0) FinishReadiness.NeedsMore(IncompleteReason(STARTED, missing)) else FinishReadiness.Ready
    }

    override fun complete(draft: QueensDraft, random: Random, budget: Budget): Completion<QueensDraft> {
        if (QueensCrowns.conflicts(draft).isNotEmpty()) return Completion.GaveUp
        val growth = QueensPinnedGrowth(draft, budget)
        repeat(MAX_ATTEMPTS) {
            if (budget.exhausted()) return Completion.GaveUp
            val board = growth.attempt(Random(random.nextLong()))
            if (board != null && finishable(board, budget)) return Completion.Done(board)
        }
        return Completion.GaveUp
    }

    /**
     * [QueensDraftAnalyzer]'s Unique and not needs-guessing. The ladder is run one deduction
     * at a time here because a whole run takes seconds on a hard size 12 board and cannot be
     * stopped: the budget is polled between deductions.
     */
    private fun finishable(board: QueensDraft, budget: Budget): Boolean {
        val puzzle = QueensPuzzle(board.size, board.regions)
        val answers = (QueensSearch.bounded(puzzle, limit = 2, budget = budget) as? QueensSearchOutcome.Done)?.solutions
        if (answers?.size != 1) return false
        var candidates = QueensCandidates.start(puzzle, placed = emptySet())
        while (!budget.exhausted()) {
            val deduction = QueensLadder.Default.next(puzzle, candidates) ?: return candidates.queens.size == board.size
            candidates = when (deduction) {
                is QueensDeduction.Place -> candidates.place(puzzle, deduction.cell)
                is QueensDeduction.Exclude -> candidates.exclude(deduction.cells)
            }
        }
        return false
    }
}
