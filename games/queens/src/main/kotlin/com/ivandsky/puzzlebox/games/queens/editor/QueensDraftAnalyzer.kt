package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch

/**
 * A Queens draft → its [Verdict] (spec A7). The board must first be fully painted with
 * exactly `size` regions; then the exhaustive solver counts up to two solutions, so
 * Unique, Multiple and None are exact. The budget is checked before the search; polling
 * inside the search loop lands with the solver budget (ticket 04).
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
        if (budget.exhausted()) return Verdict.Undetermined
        val found = QueensSearch.solutions(QueensPuzzle(draft.size, draft.regions), limit = 2)
            .map { solution -> solution.map { it.idx }.sorted() }
        return when (found.size) {
            0 -> Verdict.None(emptySet())
            1 -> Verdict.Unique(Solution(found[0]))
            else -> {
                val (a, b) = found
                Verdict.Multiple(Solution(a), Solution(b), (a.toSet() - b.toSet()) + (b.toSet() - a.toSet()))
            }
        }
    }
}
