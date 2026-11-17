package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.ClosestCompletion
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
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
 * Asked for a level, the way the Studio asks, the author's paint comes first and the crowns
 * second ([QueensPaintFirst], ticket 107):
 *
 * 1. Fill: [QueensPinnedGrowth] paints the unpainted cells; every painted cell keeps its region
 *    and every crown stays on a queen of the answer. An empty cell goes to a colour the author
 *    has not used before it grows a painted region.
 * 2. Move a crown: when no such fill has one answer, the fewest crowns that must go move along
 *    their rows to the answer's queens, and the paint still stays.
 * 3. Repaint: only when the paint itself has several answers or none (a board painted by hand,
 *    often; a fully painted one has nothing left to fill), [QueensRepaint] recolours as few
 *    painted cells as it finds, keeping the crowns when an answer can hold them.
 *    The finished board carries the cells it recoloured ([QueensDraft.recoloured]).
 *
 * The fill goes on past its first board (91): every board it finds is graded by
 * [QueensScoredGrader] and the first at the target is returned, else the closest found. Steps 2
 * and 3 are not steered: they keep as much of the author's work as they can, at whatever level.
 *
 * Without a level ("any") the order is the one before 107, kept for its pinned boards: fill,
 * then repaint with the crowns kept, then move a crown.
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
    internal const val PINNED_ATTEMPTS = 192

    /**
     * Fills tried per cell of the board for a target level, once one of the first
     * [PINNED_ATTEMPTS] has finished: a bigger board has more fills to look through. The Studio's
     * budget ends the search sooner on a large board; this ends it under an unlimited one, and
     * on a small board whose fills cannot reach the level. Set from `QueensScoredMeasurement`.
     */
    private const val TARGET_ATTEMPTS_PER_CELL = 128

    fun minimumStarted(size: Int): Int = MIN_STARTED_BY_SIZE[size - QueensVariants.MIN_SIZE]

    /** Blocked on clashing crowns ([QueensCrowns.conflicts]); NeedsMore below [minimumStarted]. */
    override fun readiness(draft: QueensDraft): FinishReadiness {
        val clashing = QueensCrowns.conflicts(draft)
        if (clashing.isNotEmpty()) return FinishReadiness.Blocked(clashing)
        val started = draft.regions.filter { it != QueensDraft.UNPAINTED }.distinct().size
        val missing = minimumStarted(draft.size) - started
        return if (missing > 0) FinishReadiness.NeedsMore(IncompleteReason(STARTED, missing)) else FinishReadiness.Ready
    }

    /**
     * [target] null is "any": the first finished board, as before levels. Otherwise the first board
     * at [target], or the closest one found when the tries or [budget] run out, in the order of
     * [QueensPaintFirst].
     */
    override fun complete(draft: QueensDraft, random: Random, budget: Budget, target: Difficulty.Level?): Completion<QueensDraft> {
        if (QueensCrowns.conflicts(draft).isNotEmpty()) return Completion.GaveUp
        val board = when (target) {
            null -> finish(draft, random, budget, target = null) ?: recrowned(draft, random, budget, target = null)
            else -> QueensPaintFirst(draft, random, budget).finish(target)
        } ?: return Completion.GaveUp
        val recoloured = draft.regions.indices.filterTo(LinkedHashSet()) { draft.regions[it] != QueensDraft.UNPAINTED && draft.regions[it] != board.regions[it] }
        return Completion.Done(board.copy(recoloured = recoloured))
    }

    /** The painted cells the finish recoloured ([QueensDraft.recoloured]). */
    override fun changedPieces(draft: QueensDraft): Int = draft.recoloured.size

    /** [draft] finished with every crown where it is: by a fill when one works, else by a repaint. */
    private fun finish(draft: QueensDraft, random: Random, budget: Budget, target: Difficulty.Level?): QueensDraft? {
        val growth = QueensPinnedGrowth(draft, budget)
        if (QueensDraft.UNPAINTED in draft.regions) filled(growth, draft.regions.size, PINNED_ATTEMPTS, random, budget, target)?.let { return it }
        return repainted(draft, growth, random, budget)
    }

    /**
     * A finishable board among [growth]'s attempts on a draft of [cells] cells: the first one when
     * [target] is null, else the first at [target] or the closest found. Null when the first
     * [tries] attempts finish none (a draft the fill cannot finish, target or not) or [budget]
     * runs out before one does.
     */
    internal fun filled(growth: QueensPinnedGrowth, cells: Int, tries: Int, random: Random, budget: Budget, target: Difficulty.Level?): QueensDraft? {
        val closest = ClosestCompletion<QueensDraft>(target)
        for (attempt in 0 until if (target == null) tries else TARGET_ATTEMPTS_PER_CELL * cells) {
            if (budget.exhausted() || (attempt == tries && closest.board() == null)) break
            val board = growth.attempt(Random(random.nextLong()))
            if (board != null && finishable(board, budget) && closest.offer(board, target?.let { level(board, budget) })) return board
        }
        return closest.board()
    }

    /** [draft] with as few painted cells recoloured as [QueensRepaint] finds and every crown where it is. */
    internal fun repainted(draft: QueensDraft, growth: QueensPinnedGrowth, random: Random, budget: Budget): QueensDraft? {
        val repaint = QueensRepaint(draft, budget) { finishable(QueensDraft(draft.size, it.toList()), budget) }
        return repaint.repaint(growth, random)?.let { QueensDraft(draft.size, it.toList(), draft.crowns) }
    }

    private fun ClosestCompletion<QueensDraft>.board(): QueensDraft? = (completion() as? Completion.Done)?.draft

    /** The scored level of a finishable [board]; null when [budget] ran out while grading it. */
    private fun level(board: QueensDraft, budget: Budget): Difficulty.Level? =
        QueensScoredGrader.grade(QueensPuzzle(board.size, board.regions), budget)?.level

    /**
     * [draft] finished with the fewest crowns moved: one given up, then two, and so on, each
     * moved along its row to the queen of the finished board's answer. A choice of crowns that
     * no answer can hold is skipped without a repaint.
     */
    internal fun recrowned(draft: QueensDraft, random: Random, budget: Budget, target: Difficulty.Level?): QueensDraft? {
        val crowns = draft.crowns.shuffled(random)
        for (gone in 1..crowns.size) {
            for (first in 0 until if (gone == crowns.size) 1 else crowns.size) {
                if (budget.exhausted()) return null
                val kept = crowns.toSet() - List(gone) { crowns[(first + it) % crowns.size] }.toSet()
                val relaxed = draft.copy(crowns = kept)
                if (QueensPinnedGrowth(relaxed, budget).fill(Random(random.nextLong())) == null) continue
                return moved(draft, finish(relaxed, random, budget, target) ?: continue)
            }
        }
        return null
    }

    /** [board] with every crown of [draft] on the queen of [board]'s one answer in the crown's row. */
    internal fun moved(draft: QueensDraft, board: QueensDraft): QueensDraft {
        val answer = QueensSearch.solutions(QueensPuzzle(board.size, board.regions), limit = 1).single().map { it.idx }
        return board.copy(crowns = draft.crowns.mapTo(LinkedHashSet()) { crown -> answer.single { it / draft.size == crown / draft.size } })
    }

    /**
     * [QueensDraftAnalyzer]'s Unique and not needs-guessing. The ladder is run one deduction
     * at a time here because a whole run takes seconds on a hard size 12 board and cannot be
     * stopped: the budget is polled between deductions.
     */
    internal fun finishable(board: QueensDraft, budget: Budget): Boolean {
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
