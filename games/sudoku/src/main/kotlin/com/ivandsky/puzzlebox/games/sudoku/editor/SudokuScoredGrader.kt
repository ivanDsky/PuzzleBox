package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.ScoredGrader
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants

/**
 * Sudoku's scored difficulty (92): the label of a board by the score of its whole solve
 * ([SudokuScoredSolve]), with the level read from the game's [SudokuLevels]. No label when
 * the [Budget] is spent or the solve gets stuck.
 */
object SudokuScoredGrader : ScoredGrader<SudokuDraft> {
    override fun grade(draft: SudokuDraft, solution: Solution, budget: Budget): Difficulty? =
        grade(requireNotNull(SudokuVariants.bySize(draft.size)).geometry, draft.givens.toIntArray(), budget)

    /** The label of [givens], which it only reads: what [SudokuTargetFinish] steers by. */
    internal fun grade(geometry: SudokuGeometry, givens: IntArray, budget: Budget): Difficulty? {
        val tally = SudokuScoredSolve.of(geometry, givens, budget)?.takeIf { it.solved } ?: return null
        return SudokuLevels.bands(geometry.size).difficulty(tally.score, tally.hardest)
    }
}
