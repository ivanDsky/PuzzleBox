package com.ivandsky.puzzlebox.domain

/** The Results rank row: where the player stands on today's board, or why there is no row. */
sealed interface ResultRank {
    /** Loading, unavailable, or the player's own entry is not posted yet: no row. */
    data object Hidden : ResultRank

    /** Signed out: the row offers sign in. */
    data object SignedOut : ResultRank

    /** 1-based [position] among [count] entries; [topPercent] is `max(1, ceil(position / count * 100))`. */
    data class Ranked(val position: Int, val count: Int, val topPercent: Int) : ResultRank
}
