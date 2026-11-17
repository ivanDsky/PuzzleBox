package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.QueensCommand
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.gameui.MoveHaptic

/**
 * The haptic for the step from [before] to [after]. Ticks once for each queen or cross placed or removed by a tap,
 * once per newly crossed cell of a paint drag, and once per lifted cross on every step of an erase swipe. Queens
 * refuses no input, so it never answers [MoveHaptic.Rejected]. The solving queen ticks like any other, and the solve
 * confirm follows it. Undo, redo, clear, hints and auto-cross are silent.
 */
internal fun queensMoveHaptic(before: QueensSession, after: QueensSession): MoveHaptic? {
    val was = before.history
    val now = after.history
    if (now.undone.isNotEmpty()) return null
    val command = now.done.lastOrNull() ?: return null
    // An erase swipe widens its one command in place: the history keeps its length, the run grows.
    if (now.done.size == was.done.size) {
        return if (command.widens(was.done.last())) MoveHaptic.Placed else null
    }
    // Several commands between two reads grow the history by more than one and still tick once.
    if (now.done.size < was.done.size) return null
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

private fun QueensCommand.widens(last: QueensCommand): Boolean =
    this is QueensCommand.EraseCrosses && last is QueensCommand.EraseCrosses &&
        cells.size > last.cells.size && cells.containsAll(last.cells)
