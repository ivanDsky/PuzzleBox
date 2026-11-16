package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.ClosestCompletion
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import kotlin.random.Random

/**
 * Finish for me aimed at a level (92). Each try starts from the board [SudokuCompleter] makes
 * with no target and tunes it one given at a time: a board easier than [target] loses a given
 * the author did not place, a harder one gains a digit of its solution. Every board on the
 * way is one the hint ladder finishes, graded by [SudokuScoredGrader] and offered to a
 * [ClosestCompletion], so a level out of reach still ends with the closest board found.
 * A try that runs out of givens to move starts over from a new board, until [budget] is spent.
 */
internal class SudokuTargetFinish private constructor(
    private val draft: SudokuDraft,
    private val target: Difficulty.Level,
    private val random: Random,
    private val budget: Budget,
) {
    private val geometry = requireNotNull(SudokuVariants.bySize(draft.size)).geometry
    private val closest = ClosestCompletion<SudokuDraft>(target)

    /** True once a board is at the target or the budget is spent: the search ends. */
    private var over = false

    private fun run(): Completion<SudokuDraft> {
        repeat(TRIES) {
            val first = SudokuCompleter.first(draft, random, budget) as? Completion.Done ?: return closest.completion()
            val givens = first.draft.givens.toIntArray()
            tune(givens)
            // A draft the ladder already finishes has no given to lose, and every try would end on it.
            if (over || first.draft == draft && closest.completion() == first) return closest.completion()
        }
        return closest.completion()
    }

    private fun tune(givens: IntArray) {
        var label = label(givens) ?: return
        var grid: List<Int>? = null
        repeat(geometry.cellCount) {
            if (over) return
            label = if (label.level < target) {
                harder(givens)
            } else {
                easier(givens, grid ?: checkNotNull(SudokuSolver.solve(geometry, givens.asList()).solution).also { grid = it })
            } ?: return
        }
    }

    /**
     * Takes away the given whose loss raises the score most without passing the target, of
     * the first [PICKS] the ladder can do without; null when there is none.
     */
    private fun harder(givens: IntArray): Difficulty? {
        var best: Difficulty? = null
        var bestCell = -1
        var found = 0
        for (cell in givens.indices.filter { draft.givens[it] == 0 && givens[it] != 0 }.shuffled(random)) {
            val digit = givens[cell]
            givens[cell] = 0
            val label = label(givens)
            if (over) return label
            givens[cell] = digit
            if (label == null || label.level > target) continue
            if (best == null || checkNotNull(label.score) > checkNotNull(best.score)) {
                best = label
                bestCell = cell
            }
            if (++found == PICKS) break
        }
        if (bestCell >= 0) givens[bestCell] = 0
        return best
    }

    /**
     * Adds the digit of [grid] that lowers the score most without passing the target, of
     * [PICKS] open cells; when every one of them passes it, the one that passes it least.
     */
    private fun easier(givens: IntArray, grid: List<Int>): Difficulty? {
        var best: Difficulty? = null
        var bestCell = -1
        for (cell in givens.indices.filter { givens[it] == 0 }.shuffled(random).take(PICKS)) {
            givens[cell] = grid[cell]
            val label = label(givens)
            if (over) return label
            givens[cell] = 0
            if (label == null) continue
            val better = best == null || when {
                label.level < target -> best.level < target && checkNotNull(label.score) > checkNotNull(best.score)
                else -> best.level < target || checkNotNull(label.score) < checkNotNull(best.score)
            }
            if (better) {
                best = label
                bestCell = cell
            }
        }
        if (bestCell >= 0) givens[bestCell] = grid[bestCell]
        return best
    }

    /**
     * The scored label of [givens] when the hint ladder finishes them, offered to [closest];
     * null for a board it does not finish. Sets [over] on a hit and once the budget is spent.
     */
    private fun label(givens: IntArray): Difficulty? {
        val label = SudokuScoredGrader.grade(geometry, givens, budget)
        val unreached = if (label == null) null else SudokuCompleter.unreached(geometry, givens, budget)
        if (budget.exhausted()) over = true
        if (label == null || unreached == null || unreached.isNotEmpty()) return null
        if (closest.offer(SudokuDraft(draft.size, givens.toList()), label.level)) over = true
        return label
    }

    companion object {
        /**
         * Boards tuned before the search settles for the closest, so a level out of reach
         * does not cost the whole budget. A 6×6 Expert, the rarest board, comes within 200.
         */
        private const val TRIES = 1_000

        /** Givens weighed against each other for each one moved. */
        private const val PICKS = 3

        fun complete(draft: SudokuDraft, target: Difficulty.Level, random: Random, budget: Budget): Completion<SudokuDraft> =
            SudokuTargetFinish(draft, target, random, budget).run()
    }
}
