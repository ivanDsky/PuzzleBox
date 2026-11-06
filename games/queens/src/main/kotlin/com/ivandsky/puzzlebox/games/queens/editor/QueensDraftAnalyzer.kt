package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearchOutcome

/**
 * A Queens draft → its [Verdict] (spec A7/A8). The board must first be fully painted with
 * exactly `size` regions; then the exhaustive solver counts up to two solutions, so
 * Unique, Multiple and None are exact. Multiple carries both witnesses and the cells they
 * differ on; None carries [QueensClashLocator]'s clash cells (empty when no structural clash
 * explains it). Unique carries the [QueensLadder.Default] difficulty, or needs guessing when
 * the ladder stalls (A9). The search polls [budget] inside its loop (ticket 04); a spent or
 * cancelled budget reads as [Verdict.Undetermined].
 */
object QueensDraftAnalyzer {
    const val UNPAINTED: String = "queens.unpainted"   // IncompleteReason.count = unpainted cells
    const val REGIONS: String = "queens.regions"       // IncompleteReason.count = painted region count ≠ size

    /** Every IncompleteReason id analyze can emit (QueensCopyCoverageTest, 25). */
    val reasonIds: Set<String> = setOf(UNPAINTED, REGIONS)

    fun analyze(draft: QueensDraft, budget: Budget): Verdict {
        val unpainted = draft.regions.count { it == QueensDraft.UNPAINTED }
        if (unpainted > 0) return Verdict.Incomplete(IncompleteReason(UNPAINTED, unpainted))
        val regionCount = draft.regions.distinct().size
        if (regionCount != draft.size) return Verdict.Incomplete(IncompleteReason(REGIONS, regionCount))
        val puzzle = QueensPuzzle(draft.size, draft.regions)
        val outcome = QueensSearch.bounded(puzzle, limit = 2, budget = budget)
        if (outcome !is QueensSearchOutcome.Done) return Verdict.Undetermined
        val found = outcome.solutions.map { solution -> solution.map { it.idx }.sorted() }
        return when (found.size) {
            0 -> Verdict.None(QueensClashLocator.conflicts(puzzle))
            1 -> unique(puzzle, Solution(found[0]))
            else -> {
                val (a, b) = found
                Verdict.Multiple(Solution(a), Solution(b), (a.toSet() - b.toSet()) + (b.toSet() - a.toSet()))
            }
        }
    }

    private fun unique(puzzle: QueensPuzzle, solution: Solution): Verdict.Unique {
        val run = QueensLadder.Default.run(puzzle)
        return if (run.solved) {
            Verdict.Unique(solution, difficulty = run.hardest?.let(::Difficulty))
        } else {
            Verdict.Unique(solution, needsGuessing = true)
        }
    }
}
