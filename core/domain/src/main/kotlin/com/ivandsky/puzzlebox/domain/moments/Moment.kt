package com.ivandsky.puzzlebox.domain.moments

/**
 * One celebrated fact about a counted solve (L1). The kinds are a closed set, declared strongest first;
 * [detectMoments] returns them in that order.
 */
sealed interface Moment {

    /** The solve is the game's new best. */
    data object PersonalBest : Moment

    /** The game's first hint-free solve under [thresholdMs], the tightest threshold it went under. */
    data class FirstSubX(val thresholdMs: Long) : Moment

    /** The player holds [place], 1 to 3, on today's Friends board. */
    data class TopThreeFriends(val place: Int) : Moment

    /** This solve moved the player above the friend [name] on today's Friends board. */
    data class PassedFriend(val uid: String, val name: String) : Moment

    /** This solve finished today's set. */
    data object FullSetDone : Moment

    /** This solve brought the streak to [days]. */
    data class Streak(val days: Int) : Moment
}
