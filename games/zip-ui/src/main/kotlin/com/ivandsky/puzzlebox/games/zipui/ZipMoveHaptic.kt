package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.gameui.MoveHaptic
import com.ivandsky.puzzlebox.games.zip.ZipCommand
import com.ivandsky.puzzlebox.games.zip.ZipSession

/**
 * What [zipMoveHaptic] reads: the live session and the feedback's refusal counter. Both are counters, so a second
 * refusal or rejection of the same kind still changes the snapshot.
 */
data class ZipMoveSnapshot(val session: ZipSession?, val refusals: Int)

/**
 * The haptic for the step from [before] to [after]: a tick when the trace grows or shrinks by a step the player made,
 * a buzz when a step was refused or invalid. The solving step is silent (the solve confirm owns it), and so are undo,
 * redo, clear and hint.
 */
internal fun zipMoveHaptic(before: ZipMoveSnapshot, after: ZipMoveSnapshot): MoveHaptic? {
    val was = before.session ?: return null
    val now = after.session ?: return null
    if (now.isComplete) return null
    if (rejections(after) > rejections(before)) return MoveHaptic.Rejected
    if (now.hintsUsed != was.hintsUsed) return null
    val command = now.history.done.lastOrNull() ?: return null
    // Undo leaves the reverted command on the redo stack, and a redo that empties it has the shape of an edit.
    if (now.history.undone.isNotEmpty() || command === was.history.done.lastOrNull()) return null
    if (was.history.undone.any { it === command }) return null
    return when (command) {
        is ZipCommand.Extend, is ZipCommand.RetractTo -> MoveHaptic.Placed
        is ZipCommand.Reset -> null
    }
}

private fun rejections(snapshot: ZipMoveSnapshot): Int = (snapshot.session?.mistakeCount ?: 0) + snapshot.refusals
