package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.PLACE_IT
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightAction
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The "Place it" action a Mini Sudoku hint's callout carries: one tap puts the explained digit in the explained
 * cell. A single and a reveal explain a cell and its digit, so they get the action; an elimination clears candidates and a
 * mistake names a wrong entry, not a move to make (A3), so they never do. [place] is the board's own placement, so the
 * digit is the player's own move. The result is [this] when the step has no action.
 */
internal fun Spotlight.withPlaceIt(step: SudokuStep, place: (idx: Int, digit: Int) -> Unit): Spotlight {
    val (idx, digit) = when (step) {
        is SudokuStep.Single -> step.place.cell to step.place.digit
        is SudokuStep.Reveal -> step.cell to step.digit
        is SudokuStep.Elimination, is SudokuStep.Mistake, SudokuStep.Solved -> return this
    }
    val bubble = callout ?: return this
    return copy(callout = bubble.copy(action = SpotlightAction(PLACE_IT) { place(idx, digit) }))
}
