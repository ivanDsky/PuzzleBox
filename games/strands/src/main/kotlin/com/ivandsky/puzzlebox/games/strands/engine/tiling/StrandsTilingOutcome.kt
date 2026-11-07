package com.ivandsky.puzzlebox.games.strands.engine.tiling

/** What a budgeted tiling search found: up to its limit of tilings, or nothing once the budget ran out. */
sealed interface StrandsTilingOutcome {
    /** Each tiling: word index per cell (row-major), at most `limit`. */
    data class Done(val tilings: List<List<Int>>) : StrandsTilingOutcome
    data object Exhausted : StrandsTilingOutcome
}
