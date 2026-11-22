package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice

/**
 * How many moves a focus board of a technique trains on a puzzle: the deductions Show me makes from where the board opens
 * until the easiest rung is another technique ([SudokuPractice.isDone]), each a trained turn. A technique can come up
 * more often in a puzzle than that (its points are not consecutive); the board only plays the longest run, so that run
 * is what counts, not [com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate.targetSteps].
 */
internal object SudokuFocusMoves {

    /** The moves of the focus board of [technique] on [puzzle]; null when [puzzle] gives no such board (see [SudokuTrainer.practice]). */
    fun of(puzzle: Puzzle, technique: TechniqueId): Int? {
        if (puzzle.gameId != SudokuGames.MINI.id) return null
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return null
        var board = SudokuPractice.start(decoded, technique, BoardKind.Focus) ?: return null
        var moves = 0
        while (!SudokuPractice.isDone(decoded, board, technique, BoardKind.Focus)) {
            val step = SudokuPractice.showMe(decoded, board)?.step ?: break
            board = SudokuPractice.commands(board, step).fold(board) { acc, command -> command.apply(acc) }
            moves++
        }
        return moves
    }
}
