package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * How the answer crowns sit against the verdict (round 6); pure, no solver call: it reads
 * the solutions the verdict already carries, so a crown-only edit never needs a new solve.
 */
sealed interface QueensAnswerFit {
    /** No crowns, or a verdict without solutions to compare (Incomplete, Checking, Undetermined). */
    data object Silent : QueensAnswerFit
    /** Unique and crowns ⊆ the solution's queens. Probe: "the one solution keeps them ✓". */
    data object Kept : QueensAnswerFit
    /** None, or Unique with a crown off the solution. Probe: "no solution has all of them". */
    data object Broken : QueensAnswerFit
    /**
     * Multiple. [authorIsA]: witness A holds at least as many crowns as B (tie ⇒ A).
     * [ghosts] = the other witness's queen cells − crowns. [movable] = ghost cells with a
     * 4-neighbour in a different region (the cells whose recolour can kill the other answer).
     */
    data class Ambiguous(val authorIsA: Boolean, val ghosts: Set<Int>, val movable: Set<Int>) : QueensAnswerFit

    companion object {
        fun of(draft: QueensDraft, verdict: Verdict): QueensAnswerFit {
            if (draft.crowns.isEmpty()) return Silent
            return when (verdict) {
                is Verdict.Unique -> if (verdict.solution.values.containsAll(draft.crowns)) Kept else Broken
                is Verdict.None -> Broken
                is Verdict.Multiple -> {
                    val authorIsA = held(draft, verdict, b = false) >= held(draft, verdict, b = true)
                    val ghosts = ghostsOf(draft, verdict, b = authorIsA)
                    Ambiguous(authorIsA, ghosts, ghosts.filterTo(LinkedHashSet()) { touchesAnotherRegion(draft, it) })
                }
                is Verdict.Incomplete, Verdict.Checking, Verdict.Undetermined -> Silent
            }
        }

        /** Ghosts for an explicit witness ([b] = Multiple.solutionB); 31's A/B toggle. */
        fun ghostsOf(draft: QueensDraft, verdict: Verdict.Multiple, b: Boolean): Set<Int> =
            witness(verdict, b).filterTo(LinkedHashSet()) { it !in draft.crowns }

        private fun witness(verdict: Verdict.Multiple, b: Boolean): List<Int> =
            (if (b) verdict.solutionB else verdict.solutionA).values

        private fun held(draft: QueensDraft, verdict: Verdict.Multiple, b: Boolean): Int =
            witness(verdict, b).count { it in draft.crowns }

        private fun touchesAnotherRegion(draft: QueensDraft, cell: Int): Boolean {
            val n = draft.size
            val row = cell / n
            val col = cell % n
            val neighbours = listOfNotNull(
                (cell - n).takeIf { row > 0 },
                (cell + n).takeIf { row < n - 1 },
                (cell - 1).takeIf { col > 0 },
                (cell + 1).takeIf { col < n - 1 },
            )
            return neighbours.any { draft.regions[it] != draft.regions[cell] }
        }
    }
}
