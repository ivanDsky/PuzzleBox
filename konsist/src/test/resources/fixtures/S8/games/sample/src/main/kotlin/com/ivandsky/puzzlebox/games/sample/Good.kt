package com.ivandsky.puzzlebox.games.sample

import android.util.Log

/** The crown, the leaderboard and the colour of this phone: KDoc is not copy. */
class LeaderboardCrownColour(val rounds: Int, val favouriteColour: Int)

private const val TAG = "LeaderboardSubmit"

// colour, crown and leaderboard in a comment are fine.
fun good(c: LeaderboardCrownColour) {
    val ok = "Ranks, queen, color and this device, with ${c.rounds} puzzles"
    val surrounds = "It surrounds the board; the grounds are fine"
    val key = "leaderboards"
    val us = "Canceled, with neighbors"
    val field = "cancelled"
    val parsed = malformed("malformed crowns section")
    val tag = "duel.rounds.${c.rounds}"
    Log.w("Ranks", "leaderboard submit rejected for this phone")
    Log.d(
        "Ranks",
        "crown colour rounds",
    )
    require(c.rounds > 0) { "crown cell outside the colour region" }
    check(c.rounds > 0, { "rounds must be positive" })
    error("leaderboard unavailable on this phone")
    println("$ok $surrounds $key $tag")
}
