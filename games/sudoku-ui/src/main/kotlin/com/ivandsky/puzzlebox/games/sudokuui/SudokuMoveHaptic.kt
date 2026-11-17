package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.gameui.MoveHaptic
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession

/** What [sudokuMoveHaptic] reads: the session plus the board's refusal counter at one instant. */
internal data class SudokuMoveSnapshot(val session: SudokuSession, val refusals: Int)

/**
 * The haptic for one player step. A refused tap on a given buzzes even though it changes nothing,
 * which is why the counter is compared rather than the session. A solving entry is silent: the
 * solve Confirm owns that moment, and a tick on top would double it.
 */
internal fun sudokuMoveHaptic(before: SudokuMoveSnapshot, after: SudokuMoveSnapshot): MoveHaptic? {
    if (after.refusals != before.refusals) return MoveHaptic.Rejected
    val command = recordedCommand(before.session, after.session) ?: return null
    return when (command) {
        is SudokuCommand.Place, is SudokuCommand.Erase, is SudokuCommand.ToggleNote -> {
            val solves = !before.session.isComplete && after.session.isComplete
            if (solves) null else MoveHaptic.Placed
        }
        is SudokuCommand.HintReveal, is SudokuCommand.Clear -> null
    }
}
