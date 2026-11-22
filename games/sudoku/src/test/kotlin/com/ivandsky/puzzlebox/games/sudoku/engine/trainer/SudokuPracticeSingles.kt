package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper

/**
 * Every single on this start's board as (cell, digit), asked of the stepper's own scans and not of the ladder's
 * `isSingle` that the judge uses: a last cell, a hidden single in a box, row or column, or a naked single, over the
 * notes the board shows. Two separate code paths that must agree.
 */
internal fun SudokuTrainerFixtures.PracticeStart.singlePlacements(): Set<Pair<Int, Int>> {
    val scan = SudokuStepper.Scan(puzzle.geometry, board, SudokuLadder.candidates(puzzle, board), lastMove = null)
    return (scan.lastCells() + scan.hiddenSingles(SudokuTechnique.HIDDEN_SINGLE_BOX) + scan.hiddenSingles(SudokuTechnique.HIDDEN_SINGLE_LINE) + scan.nakedSingles())
        .map { it.single.place.cell to it.single.place.digit }.toSet()
}
