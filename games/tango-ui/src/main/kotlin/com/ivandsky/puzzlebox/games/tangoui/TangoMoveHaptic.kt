package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoPlace
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.gameui.MoveHaptic

/**
 * The haptic for the step from [before] to [after]: a tick when the player landed a mark, rule break or not. Tango
 * refuses only a tap on a given, which is inert, so it never answers [MoveHaptic.Rejected].
 */
internal fun tangoMoveHaptic(before: TangoSession?, after: TangoSession?): MoveHaptic? {
    if (before == null || after == null) return null
    // The solve confirm owns the solving mark.
    if (after.isComplete) return null
    val was = before.history
    val now = after.history
    // Marks that land between two reads grow the history by more than one and still tick once.
    if (now.done.size <= was.done.size || now.undone.isNotEmpty()) return null
    val command = now.done.last()
    // A redo that empties the redo stack has the shape of a placement, but moves the undone command itself.
    return if (command is TangoPlace && was.undone.none { it === command }) MoveHaptic.Placed else null
}
