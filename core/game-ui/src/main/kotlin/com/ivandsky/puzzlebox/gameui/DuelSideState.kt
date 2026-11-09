package com.ivandsky.puzzlebox.gameui

/** What one side of a duel has to show: a result, a hidden result, or no result. */
sealed interface DuelSideState {
    /** A finished run: [timeMs] and the [hints] used. */
    data class Time(val timeMs: Long, val hints: Int) : DuelSideState

    /** The result exists but stays hidden until the viewer has played. */
    data object Sealed : DuelSideState

    data object NotPlayed : DuelSideState

    data object GaveUp : DuelSideState
}
