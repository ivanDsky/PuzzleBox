package com.ivandsky.puzzlebox.domain.ranks

/** What the viewer may do with another player from a board row (A19, A21). */
data class PlayerActions(val challenge: Challenge, val watch: Watch) {

    enum class Challenge {
        Allowed,

        /** The player's tier is below Duel, or it could not be read. */
        Private,
    }

    enum class Watch {
        Allowed,

        /** The player does not share replays, or their tier could not be read. */
        Private,

        /** The viewer has not solved that puzzle yet: no spoilers. */
        SolveFirst,

        /** No single puzzle to watch (Overall, a week), or replays are off. */
        Unavailable,
    }
}
