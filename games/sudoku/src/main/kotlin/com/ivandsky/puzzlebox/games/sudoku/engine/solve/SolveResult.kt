package com.ivandsky.puzzlebox.games.sudoku.engine.solve

/**
 * Outcome of [SudokuSolver.solve]: [count] solutions found, capped at the requested limit,
 * and the first one found ([solution], row-major digits), or null when there is none.
 */
data class SolveResult(val count: Int, val solution: List<Int>?)
