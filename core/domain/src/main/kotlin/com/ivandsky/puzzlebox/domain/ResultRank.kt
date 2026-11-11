package com.ivandsky.puzzlebox.domain

/** The Results rank row: where the player stands on today's board, or why there is no row. */
sealed interface ResultRank {
    /** Loading, unavailable, or the player's own entry is not posted yet: no row. */
    data object Hidden : ResultRank

    /** Signed out: the row offers sign in. */
    data object SignedOut : ResultRank

    /**
     * 1-based [position] among [count] entries; [topPercent] is `max(1, ceil(position / count * 100))`.
     * [withConnections] is the player's place once the LinkedIn connections who played the day join the board; null
     * when none of them has a time, so the row is exactly the PuzzleBox rank alone.
     */
    data class Ranked(
        val position: Int,
        val count: Int,
        val topPercent: Int,
        val withConnections: WithConnections? = null,
    ) : ResultRank

    /**
     * The 1-based [position] among [count] players and connections with a time. Equal scores share a place, so the
     * place is one more than the entries strictly ahead, as the board draws it.
     */
    data class WithConnections(val position: Int, val count: Int)
}
