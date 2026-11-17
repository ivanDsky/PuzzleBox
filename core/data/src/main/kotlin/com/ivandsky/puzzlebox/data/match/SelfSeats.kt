package com.ivandsky.puzzlebox.data.match

/**
 * The two seats of a self duel (you against your own first solves). They are fixed strings, never account
 * uids, so a self duel reads the same signed in, signed out and after switching accounts.
 */
object SelfSeats {
    /** The challenger seat: the player solving now. */
    const val ME = "self-me"

    /** The opponent seat: the first solves, frozen when the duel was created. */
    const val PAST = "self-past"
    const val PAST_NAME = "Past you"

    /** A self duel never closes: about a hundred years, so `Match.deadline` stays far away. */
    const val NO_DEADLINE_HOURS = 876_000
}

/** True for a self duel; every reader asks here instead of comparing seats itself. */
val Match.isSelf: Boolean get() = challenger.uid == SelfSeats.ME

/** The seat the player holds in this match: [SelfSeats.ME] in a self duel, else [identityUid] (null when signed out). */
fun Match.mySeat(identityUid: String?): String? = if (isSelf) SelfSeats.ME else identityUid
