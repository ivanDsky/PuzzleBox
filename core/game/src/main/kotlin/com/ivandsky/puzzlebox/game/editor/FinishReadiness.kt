package com.ivandsky.puzzlebox.game.editor

/** [DraftCompleter.readiness]: whether Finish for me is worth pressing on this board. */
sealed interface FinishReadiness {
    /** The pieces can still lead to a solution and meet the minimum. */
    data object Ready : FinishReadiness

    /** Below the per-game minimum; [reason] says what is missing. */
    data class NeedsMore(val reason: IncompleteReason) : FinishReadiness

    /** The placed pieces already contradict each other at [cells]. */
    data class Blocked(val cells: Set<Int>) : FinishReadiness
}
