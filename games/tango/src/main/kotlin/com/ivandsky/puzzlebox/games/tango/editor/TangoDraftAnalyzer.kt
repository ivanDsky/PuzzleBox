package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoSearchOutcome
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness

/**
 * A Tango draft → its [Verdict]. A board with no givens and no markers is Incomplete;
 * givens that already break a rule are None with those cells, before any search; anything else
 * goes to the bounded search for up to two solutions, so Unique, Multiple and None are exact.
 * Unique carries the [TangoGrader] difficulty, or needs guessing when the hint ladder gets
 * stuck before the board is full. Solution values are
 * [com.ivandsky.puzzlebox.games.tango.TangoCell] ordinals, row-major. A spent or cancelled
 * [Budget] reads as [Verdict.Undetermined].
 */
object TangoDraftAnalyzer {
    const val EMPTY: String = "tango.empty"      // count 0: no givens and no markers

    /** Every IncompleteReason id [analyze] can emit, so a copy table can prove it covers them all. */
    val reasonIds: Set<String> = setOf(EMPTY)

    fun analyze(draft: TangoDraft, budget: Budget): Verdict {
        if (draft.givens.isEmpty() && draft.markers.isEmpty()) return Verdict.Incomplete(IncompleteReason(EMPTY, 0))
        val puzzle = TangoPuzzle(VariantId("tango/n${TangoDraft.N}"), TangoDraft.N, draft.givens, draft.markers)
        val clash = TangoClashLocator.conflicts(puzzle)
        if (clash.isNotEmpty()) return Verdict.None(clash)
        val outcome = TangoUniqueness.bounded(puzzle, limit = 2, budget = budget)
        if (outcome !is TangoSearchOutcome.Done) return Verdict.Undetermined
        val found = outcome.solutions.map { solution -> solution.map { it.ordinal } }
        return when (found.size) {
            0 -> Verdict.None(emptySet())
            1 -> unique(puzzle, Solution(found[0]))
            else -> {
                val (a, b) = found
                Verdict.Multiple(Solution(a), Solution(b), a.indices.filter { a[it] != b[it] }.toSet())
            }
        }
    }

    private fun unique(puzzle: TangoPuzzle, solution: Solution): Verdict.Unique {
        val grade = TangoGrader.grade(puzzle)
        return if (grade.stuck) {
            Verdict.Unique(solution, needsGuessing = true)
        } else {
            Verdict.Unique(solution, difficulty = grade.hardest?.let(::Difficulty))
        }
    }
}
