package com.ivandsky.puzzlebox.domain

/** The rank-order key that decided a leg: the first key of the game's order on which the two results differ. */
enum class LegBasis { Time, Hints }

/** One leg of a duel as the viewer sees it. */
sealed interface LegVerdict {
    data class Win(val basis: LegBasis) : LegVerdict

    data class Loss(val basis: LegBasis) : LegVerdict

    data object Draw : LegVerdict

    /** Counts for neither side: authored by a player, or the match was cancelled, declined or never claimed. */
    data object Void : LegVerdict

    /** Can still change: a player has no counted result and the match has not closed. */
    data object Pending : LegVerdict
}

enum class MatchVerdict { Won, Lost, Drawn }

/**
 * [ComputeMatchOutcome]'s answer for one viewer: [myWins] and [theirWins] over non-void legs. [verdict] is null until
 * [settled], and stays null for a cancelled, declined or unclaimed match.
 */
data class Outcome(
    val legs: List<LegVerdict>,
    val myWins: Int,
    val theirWins: Int,
    val settled: Boolean,
    val verdict: MatchVerdict?,
)
