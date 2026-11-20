package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds

/**
 * Whether the walk of a Mini Sudoku stays on the eight techniques the Trainer lists. The walker counts only those, so
 * a naked or hidden triple and a reveal pass through it uncounted; a pointing drill whose solve also needs a triple
 * would show nothing of that. This plays the stepper from the givens. The listed techniques only strike candidates,
 * so they solve the same puzzles in any order: the stepper stays on them exactly when the practice walk never stalls.
 */
internal object MiniSudokuWalk {

    /** True when [puzzle] solves with only listed techniques: no reveal, no triple. False for a puzzle that does not decode. */
    fun staysListed(puzzle: Puzzle): Boolean {
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return false
        var board = SudokuBoard.of(decoded)
        var known: Map<Int, Int> = emptyMap()
        var lastMove: Int? = null
        repeat(decoded.geometry.cellCount + 40) {
            when (val step = SudokuStepper.next(decoded, board, lastMove, known)) {
                SudokuStep.Solved -> return true
                is SudokuStep.Mistake, is SudokuStep.Reveal -> return false
                is SudokuStep.Single -> {
                    board = board.with(step.place.cell, SudokuCell.Entry(step.place.digit))
                    lastMove = step.place.cell
                }
                is SudokuStep.Elimination -> {
                    if (SudokuTechniqueIds.of(step) == null) return false
                    known = known.toMutableMap().also { merged ->
                        step.struck.forEach { (cell, mask) -> merged[cell] = (merged[cell] ?: 0) or mask }
                    }
                }
            }
        }
        return false
    }
}
