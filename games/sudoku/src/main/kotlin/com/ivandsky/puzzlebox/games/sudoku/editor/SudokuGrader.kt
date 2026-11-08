package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints

/**
 * Grades a board by playing the shipped hint ladder from its givens to the end (spec A9).
 * The label is the hardest family any step used, whether as the placing single or as the
 * elimination it needed first; a reveal means no technique applied, so the board needs
 * guessing. The ladder is deterministic, and so is the grade.
 */
object SudokuGrader {
    private val hints = SudokuHints()

    fun grade(puzzle: SudokuPuzzle): SudokuGrade {
        var board = SudokuBoard.of(puzzle)
        var hardest: SudokuRung? = null
        while (true) {
            when (val hint = hints.hint(puzzle, board)) {
                is SudokuHint.Place -> {
                    hardest = listOfNotNull(hardest, SudokuRung.of(hint.technique), hint.via?.let(SudokuRung::of)).max()
                    board = board.with(hint.cell, SudokuCell.Entry(hint.digit))
                }
                is SudokuHint.Reveal -> return SudokuGrade(hardest, needsGuessing = true)
                SudokuHint.None -> return SudokuGrade(hardest, needsGuessing = false)
                is SudokuHint.Conflict, is SudokuHint.Wrong ->
                    throw IllegalArgumentException("givens and solution disagree: $hint")
            }
        }
    }
}
