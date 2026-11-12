package com.ivandsky.puzzlebox.domain.linkedin

/**
 * Whether a game day has a saved LinkedIn board, for the day card and the boards. A saved board needs no link, so
 * this says nothing about whether the player is connected.
 */
sealed interface SavedBoardState {

    /** A board was read and kept: [asOfMs] is when it was read and [final] whether it was read after the day's end. */
    data class Saved(val asOfMs: Long, val final: Boolean) : SavedBoardState

    /** No board is saved and the day is past the days LinkedIn serves one for, so none ever will be. */
    data object NeverSaved : SavedBoardState

    /** No board is saved yet, but the day is inside the days LinkedIn serves one for, so a read can still make it. */
    data object NotYetRead : SavedBoardState
}
