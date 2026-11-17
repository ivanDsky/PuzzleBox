package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.editor.QueensPinnedGrowth.Spread
import kotlin.random.Random

/**
 * Finish for me in the order the author asked for (ticket 107), which [QueensCompleter] runs for
 * a press with a level: the paint comes first and the crowns second.
 *
 * 1. Fill with every painted cell and every crown kept.
 * 2. Fill with every painted cell kept and the fewest crowns moved, each along its row to the
 *    answer's queen. No crown is dropped.
 * 3. Only when no fill has one answer, the repaint as [QueensCompleter] did it before 107: every
 *    crown kept when an answer can hold them all, else the fewest moved.
 *
 * A fill gives an empty cell to a colour the author has not used before it grows a painted region
 * ([Spread]). The first spread that finishes the draft is the one used, so a painted region grows
 * only when the board cannot be finished otherwise, and the level is looked for among the boards
 * of that spread. Step 2 takes its first board at whatever level.
 */
internal class QueensPaintFirst(private val draft: QueensDraft, private val random: Random, private val budget: Budget) {
    private val cells = draft.regions.size
    private val spreads =
        if ((0 until draft.size).all { it in draft.regions }) listOf(Spread.Free) else listOf(Spread.UnusedOnly, Spread.UnusedFirst, Spread.Free)

    fun finish(target: Difficulty.Level): QueensDraft? =
        filled(draft, target)
            ?: recrowned()
            ?: QueensCompleter.repainted(draft, QueensPinnedGrowth(draft, budget), random, budget)
            ?: QueensCompleter.recrowned(draft, random, budget, target)

    /** [pieces] filled by the first spread that finishes them; null when none does, or nothing is left to fill. */
    private fun filled(pieces: QueensDraft, target: Difficulty.Level?): QueensDraft? {
        if (QueensDraft.UNPAINTED !in pieces.regions) return null
        return spreads.firstNotNullOfOrNull { spread ->
            val tries = if (spread == Spread.Free) QueensCompleter.PINNED_ATTEMPTS else UNUSED_ATTEMPTS
            QueensCompleter.filled(QueensPinnedGrowth(pieces, budget, spread), cells, tries, random, budget, target)
        }
    }

    /**
     * Step 2. The draft without its crowns is filled first: that board, with every crown moved to
     * its answer, is the fallback, and when there is none no choice of crowns has a board either.
     * Then one crown is given up, then two, chosen as [QueensCompleter.recrowned] chooses them,
     * while that would move fewer crowns than the fallback does.
     */
    private fun recrowned(): QueensDraft? {
        if (draft.crowns.isEmpty()) return null
        val bare = draft.copy(crowns = emptySet())
        if (QueensDraft.UNPAINTED !in draft.regions) {
            return if (QueensCompleter.finishable(bare, budget)) QueensCompleter.moved(draft, bare) else null
        }
        val fallback = QueensCompleter.moved(draft, filled(bare, target = null) ?: return null)
        val crowns = draft.crowns.shuffled(random)
        for (gone in 1 until draft.crowns.count { it !in fallback.crowns }) {
            for (first in crowns.indices) {
                if (budget.exhausted()) return fallback
                val relaxed = draft.copy(crowns = crowns.toSet() - List(gone) { crowns[(first + it) % crowns.size] }.toSet())
                if (QueensPinnedGrowth(relaxed, budget).fill(Random(random.nextLong())) == null) continue
                filled(relaxed, target = null)?.let { return QueensCompleter.moved(draft, it) }
            }
        }
        return fallback
    }

    private companion object {
        /**
         * Fills tried with each spread that favours the unused colours before the next takes over.
         * A draft such a spread can finish is finished early: its empty cells have few ways to go.
         */
        const val UNUSED_ATTEMPTS = 64
    }
}
