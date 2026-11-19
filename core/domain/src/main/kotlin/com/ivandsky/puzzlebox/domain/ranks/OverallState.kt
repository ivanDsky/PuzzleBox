package com.ivandsky.puzzlebox.domain.ranks

/** What the Overall view may honestly say about a window (A12): [Partial] and [Offline] are never final. */
sealed interface OverallState {
    /** No board has answered yet. */
    data object Loading : OverallState

    /** Boards are read only when signed in (A3). */
    data object SignedOut : OverallState

    /** Every board loaded and nobody has played. */
    data object Empty : OverallState

    /** [loaded] of [total] boards are in [standings]; the rest add nothing and are not counted as played. */
    data class Partial(val standings: OverallStandings, val loaded: Int, val total: Int) : OverallState

    /** Every board of the window loaded. */
    data class Complete(val standings: OverallStandings) : OverallState

    /**
     * No open board could be read, so the device looks offline. [standings] covers only the
     * boards that did load (Final boards kept in the store), or is null when none did.
     */
    data class Offline(val standings: OverallStandings?) : OverallState
}
