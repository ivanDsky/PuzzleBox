package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver

/**
 * The feed's trust gate: a puzzle is playable only when its givens have exactly one
 * solution and that solution is the one the feed shipped. Hints and win checks read the
 * shipped solution, so a board that disagrees with it must never reach play.
 */
object SudokuUniquenessGate {

    fun isTrusted(puzzle: SudokuPuzzle): Boolean {
        val result = SudokuSolver.solve(puzzle.geometry, puzzle.givens, limit = 2)
        return result.count == 1 && result.solution == puzzle.solution
    }
}
