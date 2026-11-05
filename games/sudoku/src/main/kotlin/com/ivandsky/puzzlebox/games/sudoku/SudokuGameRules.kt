package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict

/**
 * The plugin's rules facade. Validation is total: a foreign move gets a typed verdict, never
 * a throw. Completion is "every digit matches the solution" until ticket 09 makes it
 * rule-based; [SudokuSession] reads it from here so both stay in step.
 */
object SudokuGameRules : GameRules<SudokuState> {

    /** The first cell whose digit is missing or wrong, with the solution's digit. */
    data class Reveal(val idx: Int, val digit: Int) : Hint

    /** Nothing left to hint: the board is solved. */
    data object Solved : Hint

    override fun validate(move: Move, state: SudokuState): MoveVerdict {
        if (isComplete(state)) return SudokuValidation.Complete
        return when (move) {
            is SudokuMove.Place -> digitVerdict(state, move.idx, move.digit)
            is SudokuMove.ToggleNote -> digitVerdict(state, move.idx, move.digit)
            is SudokuMove.Erase -> cellVerdict(state, move.idx)
            SudokuMove.Clear -> SudokuValidation.Valid
            else -> SudokuValidation.OutOfRange
        }
    }

    override fun isComplete(state: SudokuState): Boolean =
        state.puzzle.solution.indices.all { state.board.digitAt(it) == state.puzzle.solution[it] }

    override fun hint(state: SudokuState): Hint {
        val solution = state.puzzle.solution
        val idx = solution.indices.firstOrNull { state.board.digitAt(it) != solution[it] } ?: return Solved
        return Reveal(idx, solution[idx])
    }

    private fun digitVerdict(state: SudokuState, idx: Int, digit: Int): SudokuValidation =
        if (digit !in 1..state.board.geometry.size) SudokuValidation.OutOfRange else cellVerdict(state, idx)

    private fun cellVerdict(state: SudokuState, idx: Int): SudokuValidation = when {
        idx !in 0 until state.board.geometry.cellCount -> SudokuValidation.OutOfRange
        state.board.cells[idx] is SudokuCell.Given -> SudokuValidation.GivenCell
        else -> SudokuValidation.Valid
    }
}
