package com.ivandsky.puzzlebox.domain.lineup

/** What looking up a typed or pasted share code concluded (spec A4, A20). */
sealed interface CodeLookup {
    /** The puzzle, now saved to the player's friends' puzzles. */
    data class Found(val puzzle: PickablePuzzle) : CodeLookup

    data object NotFound : CodeLookup

    /** Its author withdrew it before it was ever opened here. */
    data object NoLongerAvailable : CodeLookup

    /** The puzzle exists but this build cannot open it: a newer format, a game it lacks, or a failed check. */
    data object CannotOpen : CodeLookup

    /** There was no connection to ask, so nothing is known about the code. */
    data object Offline : CodeLookup

    /** The text is neither a share code nor a puzzle link. */
    data object Invalid : CodeLookup
}
