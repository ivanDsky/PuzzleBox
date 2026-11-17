package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoPlace
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.gameui.MoveHaptic

/** What the Tango move haptic is read from: the session, and the rejection marker for input that leaves it untouched. */
internal data class TangoPlaySnapshot(val session: TangoSession?, val lastRejection: TangoVerdict.Rejected?)

/**
 * The haptic of the step from [before] to [after] (S2). A mark the player lands, sets or cycles back to empty, is
 * [MoveHaptic.Placed], with or without a rule break: the break shows on the board. A new rejection that landed
 * nothing is [MoveHaptic.Rejected]. Undo, redo, clear, a hint, play-again and a board with no session are null.
 */
internal fun tangoMoveHaptic(before: TangoPlaySnapshot, after: TangoPlaySnapshot): MoveHaptic? {
    if (landedMark(before.session, after.session)) return MoveHaptic.Placed
    // By instance: a second refusal of the same cell equals the first.
    val rejection = after.lastRejection
    return if (rejection != null && rejection !== before.lastRejection) MoveHaptic.Rejected else null
}

private fun landedMark(before: TangoSession?, after: TangoSession?): Boolean {
    if (before == null || after == null) return false
    val was = before.history
    val now = after.history
    if (now.done.size != was.done.size + 1 || now.undone.isNotEmpty()) return false
    val command = now.done.last()
    // Redo of the last undone command has the stack shape of a new record; redo moves the very same instance.
    return command is TangoPlace && command !== was.undone.lastOrNull()
}
