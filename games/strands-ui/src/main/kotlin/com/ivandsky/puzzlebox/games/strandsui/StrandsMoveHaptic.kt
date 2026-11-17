package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.gameui.MoveHaptic

/** What a Strands board step is read from: the live drag, the word slot's verdict, and whether the board is solved. */
internal data class StrandsMoveSnapshot(val trace: Trace, val slot: WordSlot, val solved: Boolean)

internal fun StrandsPlayController.moveSnapshot() = StrandsMoveSnapshot(trace, slot, isComplete)

/**
 * The haptic for the step from [before] to [after]. The solving move is null: the solve confirm owns
 * that moment. A new verdict is [MoveHaptic.Rejected] when refused, [MoveHaptic.Placed] otherwise. A
 * drag that grows past the last read is a placed letter; one that only shrinks is not.
 */
internal fun strandsMoveHaptic(before: StrandsMoveSnapshot, after: StrandsMoveSnapshot): MoveHaptic? {
    if (after.solved) return null
    val verdict = (after.slot as? WordSlot.Verdict)?.takeIf { it != before.slot }
    val path = after.trace.path
    return when {
        verdict != null -> if (verdict.kind == VerdictKind.Reject) MoveHaptic.Rejected else MoveHaptic.Placed
        path.isNotEmpty() && before.trace.path.take(path.size) != path -> MoveHaptic.Placed
        else -> null
    }
}
