package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.ClosestCompletion
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import kotlin.random.Random

/**
 * Finish for me aimed at a level (93): [TangoCompleter]'s search when the author picked one. Like
 * the plain Finish it keeps every given and marker of the draft and adds givens only, all read off
 * one answer [TangoPinnedFill] draws.
 *
 * A round draws an answer and adds givens where the hint ladder is stuck until it finishes the
 * board; the rules are sound, so that board has the one answer. Then it moves the board to the
 * level: a board above it gets more givens until it is not, a board below it loses added givens
 * one at a time, each kept off only while the ladder still finishes the board and the level is not
 * passed. A round that ends off the level is kept if it is the closest so far and another round
 * starts, up to [ROUNDS]. What comes back is the board at the level, else the closest found
 * ([ClosestCompletion]); GaveUp only when no round got a board at all. A draft the ladder already
 * finishes that is at or under the level comes back as it is: it has nothing to take back.
 *
 * Levels come from [TangoScoredGrader] and the size's [TangoScoreBands], so the label the Studio
 * shows after Finish is the one aimed for.
 */
internal object TangoTargetFinish {
    /**
     * Rounds before the closest board is returned: what ends the search when the budget does not.
     * A round takes about 0.25 ms at 6×6, 1 ms at 8×8 and 3 ms at 10×10 on a Mac JVM, so on a phone
     * a level out of reach uses most or all of the 4 s the Studio allows; a level in reach is hit
     * in the first few rounds (`TangoDifficultyMeasurement`).
     */
    const val ROUNDS = 2_000

    fun complete(draft: TangoDraft, random: Random, budget: Budget, target: Difficulty.Level): Completion<TangoDraft> {
        val pieces = TangoPuzzle(TangoDraft.variantOf(draft.n), draft.n, draft.givens, draft.markers)
        val walk = TangoSolveWalk(draft.n, draft.markers)
        val closest = ClosestCompletion<TangoDraft>(target)
        repeat(ROUNDS) {
            val answer = TangoPinnedFill.fill(pieces, random, budget) ?: return closest.completion()
            val round = Round(draft, answer, walk, random, budget)
            if (!round.finishByLadder()) return closest.completion()
            round.aimFor(target)
            val hit = closest.offer(draft.copy(givens = round.givens.toMap()), round.level)
            if (hit || round.spent || round.unchanged) return closest.completion()
        }
        return closest.completion()
    }

    /** One answer's board on its way to the level: the draft's givens plus the [added] ones. */
    private class Round(
        private val draft: TangoDraft,
        private val answer: List<TangoCell>,
        private val walk: TangoSolveWalk,
        private val random: Random,
        private val budget: Budget,
    ) {
        val givens: MutableMap<Int, TangoCell> = draft.givens.toMutableMap()
        private val added = ArrayList<Int>()

        /** The level of the board as it stands; null for a full board. */
        var level: Difficulty.Level? = null
            private set

        /** True once the budget ran out; the board is still one the ladder finishes. */
        var spent = false
            private set

        /** True when the board is the draft: the ladder finishes it as it is, so every round would end here. */
        val unchanged: Boolean get() = added.isEmpty()

        /** Adds givens where the ladder is stuck until it fills the board. False when the budget is spent first. */
        fun finishByLadder(): Boolean {
            while (true) {
                val tally = walk.tally(givens, budget) ?: return false
                if (tally.left == 0) {
                    level = TangoScoredGrader.difficulty(draft.n, tally)?.level
                    return true
                }
                add(walk.stuck().random(random))
            }
        }

        fun aimFor(target: Difficulty.Level) {
            ease(target)
            harden(target)
        }

        /** Gives cells away until the board is no harder than [target]; the last empty cell stays. */
        private fun ease(target: Difficulty.Level) {
            val open = (0 until draft.n * draft.n).filterNot(givens::containsKey).shuffled(random)
            for (cell in open.dropLast(1)) {
                if (above(target) != true) return
                add(cell)
                val tally = walk.tally(givens, budget)
                if (tally == null) {
                    remove(cell)
                    spent = true
                    return
                }
                level = TangoScoredGrader.difficulty(draft.n, tally)?.level
            }
        }

        /** Takes added givens back while the board stays under [target], until it is at it. */
        private fun harden(target: Difficulty.Level) {
            for (cell in added.shuffled(random)) {
                if (spent || level == target) return
                remove(cell)
                val tally = walk.tally(givens, budget)
                val lighter = tally?.takeIf { it.left == 0 }?.let { TangoScoredGrader.difficulty(draft.n, it)?.level }
                if (tally == null) spent = true
                if (lighter == null || lighter > target) add(cell) else level = lighter
            }
        }

        private fun above(target: Difficulty.Level): Boolean? = level?.let { it > target }

        private fun add(cell: Int) {
            givens[cell] = answer[cell]
            added += cell
        }

        private fun remove(cell: Int) {
            givens.remove(cell)
            added.remove(cell)
        }
    }
}
