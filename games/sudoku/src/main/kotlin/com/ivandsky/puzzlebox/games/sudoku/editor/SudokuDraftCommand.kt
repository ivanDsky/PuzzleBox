package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.Command

/**
 * Sudoku draft edits as undoable commands (spec A5). Resize and clear use
 * [com.ivandsky.puzzlebox.game.editor.ReplaceDraft], not a Sudoku command.
 */
sealed interface SudokuDraftCommand : Command<SudokuDraft> {
    /** One keypad press or Erase = one step; digit 0 erases. */
    data class SetGiven(val cell: Int, val digit: Int, val previous: Int) : SudokuDraftCommand {
        override fun apply(state: SudokuDraft): SudokuDraft = state.with(cell, digit)
        override fun revert(state: SudokuDraft): SudokuDraft = state.with(cell, previous)
    }
}

/** Set [cell] to [digit] (0 erases), capturing the current digit so the step reverts exactly. */
fun SudokuDraft.setGiven(cell: Int, digit: Int): SudokuDraftCommand.SetGiven =
    SudokuDraftCommand.SetGiven(cell, digit, givens[cell])

private fun SudokuDraft.with(cell: Int, digit: Int): SudokuDraft =
    copy(givens = givens.toMutableList().also { it[cell] = digit })
