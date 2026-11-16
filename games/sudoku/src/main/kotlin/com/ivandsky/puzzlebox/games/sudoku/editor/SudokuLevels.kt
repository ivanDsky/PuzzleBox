package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.LevelBands
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants

/**
 * Where each Sudoku game's levels start on the score of [SudokuScoredSolve] (92), shared by
 * [SudokuScoredGrader] and [SudokuTargetFinish]. The two sizes are two games: a 9×9 board
 * has more than twice the cells to place, so its scores run several times higher.
 *
 * Set from SudokuLevelsMeasurement. On 6×6 the 454 LinkedIn dailies, which singles finish
 * one and all, have a median score of 54: two in three are Easy, one in four Medium, and the
 * sparse few with a single cell to find for moves on end are Hard and Expert. On 9×9 the
 * board Finish for me makes from one given has a median of 141 and is Easy or Medium; Hard
 * and Expert take boards cut down until most steps have one or two cells to find.
 */
internal object SudokuLevels {
    private val MINI = LevelBands(medium = 65, hard = 95, expert = 125)
    private val CLASSIC = LevelBands(medium = 150, hard = 300, expert = 500)

    fun bands(size: Int): LevelBands = if (size == SudokuVariants.MINI.geometry.size) MINI else CLASSIC
}
