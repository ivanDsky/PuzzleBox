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
 * Queens' Finish for me (spec E1): paints a draft into a board with one answer the hint ladder
 * can reach, every region in one piece. A board is returned only when the engine's exhaustive
 * search and the hint ladder agree with what [QueensDraftAnalyzer] would say of it: Unique and
 * not needs-guessing. Every attempt runs on its own [Random] seeded from the injected one.
 *
 * 1. Fill: [QueensPinnedGrowth] paints the unpainted cells; every painted cell keeps its region
 *    and every crown stays on a queen of the answer.
 * 2. Repaint: when no fill gives one answer (a board painted by hand has other answers on
 *    its painted cells, and a fully painted one has nothing left to fill), [QueensRepaint]
 *    recolours as few painted cells as it finds. The crowns still stay.
 * 3. Move a crown: only when no answer can hold every crown, the fewest crowns that must go
 *    move along their rows to the answer's queens.
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
     * Fills tried before the repaint takes over. Measured on the 1440 drafts of
     * `QueensPaintedMeasurement` below 100 percent painted: of the 949 that a fill finished in
     * 1024 tries, 945 needed 192 at most, and a draft no fill can finish fails every one, so
     * more only spend the repaint's time.
     */
    private const val PINNED_ATTEMPTS = 192

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
        val board = finish(draft, random, budget) ?: recrowned(draft, random, budget) ?: return Completion.GaveUp
        return Completion.Done(board)
    }

    /** [draft] finished with every crown where it is: by a fill when one works, else by a repaint. */
    private fun finish(draft: QueensDraft, random: Random, budget: Budget): QueensDraft? {
        val growth = QueensPinnedGrowth(draft, budget)
        if (QueensDraft.UNPAINTED in draft.regions) {
            repeat(PINNED_ATTEMPTS) {
                if (budget.exhausted()) return null
                val board = growth.attempt(Random(random.nextLong()))
                if (board != null && finishable(board, budget)) return board
            }
        }
        val repaint = QueensRepaint(draft, budget) { finishable(QueensDraft(draft.size, it.toList()), budget) }
        return repaint.repaint(growth, random)?.let { QueensDraft(draft.size, it.toList(), draft.crowns) }
    }

    /**
     * [draft] finished with the fewest crowns moved: one given up, then two, and so on, each
     * moved along its row to the queen of the finished board's answer. A choice of crowns that
     * no answer can hold is skipped without a repaint.
     */
    private fun recrowned(draft: QueensDraft, random: Random, budget: Budget): QueensDraft? {
        val crowns = draft.crowns.shuffled(random)
        for (gone in 1..crowns.size) {
            for (first in 0 until if (gone == crowns.size) 1 else crowns.size) {
                if (budget.exhausted()) return null
                val kept = crowns.toSet() - List(gone) { crowns[(first + it) % crowns.size] }.toSet()
                val relaxed = draft.copy(crowns = kept)
                if (QueensPinnedGrowth(relaxed, budget).fill(Random(random.nextLong())) == null) continue
                val board = finish(relaxed, random, budget) ?: continue
                val answer = QueensSearch.solutions(QueensPuzzle(board.size, board.regions), limit = 1).single().map { it.idx }
                val moved = draft.crowns.mapTo(LinkedHashSet()) { crown -> answer.single { it / draft.size == crown / draft.size } }
                return board.copy(crowns = moved)
            }
        }
        return null
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
