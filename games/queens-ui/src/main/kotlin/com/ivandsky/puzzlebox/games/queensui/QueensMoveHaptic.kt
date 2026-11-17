package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.QueensCommand
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.gameui.MoveHaptic

/**
 * The haptic for the step from [before] to [after]: a queen or cross placed or removed, by a tap or an erase swipe,
 * ticks, rule break or not. Queens refuses no input, so it never answers [MoveHaptic.Rejected]. The solving move is
 * silent (the solve confirm owns that moment), and so are undo, redo, clear, hints and auto-cross.
 */
internal fun queensMoveHaptic(before: QueensSession, after: QueensSession): MoveHaptic? {
    if (after.isComplete) return null
    val was = before.history
    val now = after.history
    // Several commands between two reads grow the history by more than one and still tick once.
    if (now.done.size <= was.done.size || now.undone.isNotEmpty()) return null
    val command = now.done.last()
    // A redo that empties the redo stack has the shape of a placement, but replays the undone command itself.
    if (was.undone.any { it === command }) return null
    return when (command) {
        is QueensCommand.PlaceQueen,
        is QueensCommand.RemoveQueen,
        is QueensCommand.ToggleCross,
        is QueensCommand.EraseCrosses -> MoveHaptic.Placed
        is QueensCommand.Clear -> null
    }
}
