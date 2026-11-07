package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle

/** What one [LoadSharedPuzzle] call concluded about a share code. */
sealed interface OpenOutcome {
    /** Stored on this device and playable. */
    data class Opened(val puzzle: ReceivedPuzzle) : OpenOutcome

    data object NotFound : OpenOutcome

    /** Its author withdrew it before it was ever opened here. */
    data object NoLongerAvailable : OpenOutcome

    /** The document or its payload is in format [found], newer than this build reads (A24). */
    data class NeedsUpdate(val found: Int) : OpenOutcome

    /** A game this build doesn't have (A1). */
    data class UnknownGame(val gameId: String) : OpenOutcome

    /** The payload is malformed, has no single solution, or its game can't open shared boards. */
    data object FailedCheck : OpenOutcome

    data object Offline : OpenOutcome
}
