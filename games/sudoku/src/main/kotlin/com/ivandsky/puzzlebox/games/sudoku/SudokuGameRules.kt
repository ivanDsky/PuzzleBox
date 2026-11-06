package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts

/**
 * The plugin's rules facade. Validation is total: a foreign move gets a typed verdict, never
 * a throw. Completion is rule-based: any full board without a conflict is solved, the same
 * check [SudokuSession] stamps its win with. Hints climb the [SudokuHints] ladder.
 */
object SudokuGameRules : GameRules<SudokuState> {

    private val hints = SudokuHints()

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

    override fun isComplete(state: SudokuState): Boolean = SudokuConflicts.isSolved(state.board)

    override fun hint(state: SudokuState): SudokuHint = hints.hint(state.puzzle, state.board)

    private fun digitVerdict(state: SudokuState, idx: Int, digit: Int): SudokuValidation =
        if (digit !in 1..state.board.geometry.size) SudokuValidation.OutOfRange else cellVerdict(state, idx)

    private fun cellVerdict(state: SudokuState, idx: Int): SudokuValidation = when {
        idx !in 0 until state.board.geometry.cellCount -> SudokuValidation.OutOfRange
        state.board.cells[idx] is SudokuCell.Given -> SudokuValidation.GivenCell
        else -> SudokuValidation.Valid
    }
}
