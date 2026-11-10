package com.ivandsky.puzzlebox.data.match

/** Whether a code is already a puzzle's or a match's; [Unavailable] says why the server could not tell. */
sealed interface PuzzleCodeCheck {
    data object Free : PuzzleCodeCheck
    data object Taken : PuzzleCodeCheck

    /** [offline] = a retry can help; otherwise the read was refused or malformed and a retry will fail the same way. */
    data class Unavailable(val offline: Boolean, val message: String? = null) : PuzzleCodeCheck
}
