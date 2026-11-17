package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.gameui.MoveHaptic

/**
 * What a Strands board change is read from (S2). [trace] is the live drag, so a letter joining it is
 * a change; [rejections] is a counter of refused words, never the last refusal, so a second refusal of
 * the same word still differs.
 */
internal data class StrandsMoveSnapshot(
    val trace: Trace,
    val found: Int,
    val hintsUsed: Int,
    val rejections: Int,
    val solved: Boolean,
)

/** The snapshot the move-haptic effect reads: committed session state, the drag, and the refusal counter. */
internal fun StrandsPlayController.moveSnapshot(): StrandsMoveSnapshot {
    val state = session?.state
    return StrandsMoveSnapshot(
        trace = trace,
        found = state?.found?.size ?: 0,
        hintsUsed = state?.hintsUsed ?: 0,
        rejections = rejections,
        solved = state?.isComplete == true,
    )
}

/**
 * The haptic for the step from [before] to [after]. The solving move is null: the solve confirm owns
 * that moment. A hint is null. A refused word is [MoveHaptic.Rejected]; a found word, or a letter that
 * joins the drag, is [MoveHaptic.Placed].
 */
internal fun strandsMoveHaptic(before: StrandsMoveSnapshot, after: StrandsMoveSnapshot): MoveHaptic? = when {
    after.solved -> null
    after.hintsUsed > before.hintsUsed -> null
    after.rejections > before.rejections -> MoveHaptic.Rejected
    after.found > before.found -> MoveHaptic.Placed
    after.trace.path.size > before.trace.path.size -> MoveHaptic.Placed
    else -> null
}
