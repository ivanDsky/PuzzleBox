package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.gameui.MoveHaptic
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession

/** What [sudokuMoveHaptic] reads: the session plus the board's latest refusal at one instant. */
internal data class SudokuMoveSnapshot(val session: SudokuSession, val rejection: SudokuRejection?)

/**
 * The haptic for one player step. A refused tap on a given buzzes even though it changes nothing,
 * which is why the refusal is compared rather than the session. The refusal is a value with a
 * sequence number, so a repeat of the same refusal is a new value and buzzes again.
 *
 * Entries and note changes tick. Silent: the solving entry (the solve Confirm owns that moment,
 * and a tick on top would double it), erase, clear, hint reveal, the notes toggle, and undo/redo.
 */
internal fun sudokuMoveHaptic(before: SudokuMoveSnapshot, after: SudokuMoveSnapshot): MoveHaptic? {
    val refused = after.rejection
    if (refused != null && refused != before.rejection) return MoveHaptic.Rejected
    val command = recordedCommand(before.session, after.session) ?: return null
    return when (command) {
        is SudokuCommand.Place, is SudokuCommand.ToggleNote -> {
            val solves = !before.session.isComplete && after.session.isComplete
            if (solves) null else MoveHaptic.Placed
        }
        is SudokuCommand.Erase, is SudokuCommand.HintReveal, is SudokuCommand.Clear -> null
    }
}
