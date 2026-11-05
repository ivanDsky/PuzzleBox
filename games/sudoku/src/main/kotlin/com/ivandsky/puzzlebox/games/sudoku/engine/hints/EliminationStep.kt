package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry

/**
 * One candidate-eliminating technique. [candidates] holds a mask per cell (bit d-1 for digit d);
 * placed cells hold 0 and must stay 0. An implementation clears what its technique proves
 * impossible, in place, and returns whether it cleared anything. It must only ever remove
 * bits that cannot be the cell's digit, so the solution digit always stays a candidate.
 */
internal interface EliminationStep {
    val technique: SudokuTechnique

    fun eliminate(geometry: SudokuGeometry, candidates: IntArray): Boolean
}
