package com.ivandsky.puzzlebox.game.linkedin

/** How far the player's board on LinkedIn has got, as the game page serves it. */
enum class LinkedInProgress {
    /** Nothing saved: the page serves no saved play. */
    Fresh,

    /** An accepted non-final save is there: a partial board, not a result. */
    Started,

    /** A result is there, or the saved play is complete or of a kind this reader does not know. */
    Won,
}
