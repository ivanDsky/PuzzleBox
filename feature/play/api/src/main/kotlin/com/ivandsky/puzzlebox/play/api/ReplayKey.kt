package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The one replay page of every surface: the own result, the custom result, the duel leg result and History open it with
 * their own key. [gameId] names the game whose board draws it. Ids only: a timeline or its bytes never ride a key, the
 * page reads them when it opens, so the key survives process death.
 *
 * [kind] says which solve (one of [RECORD], [LATEST], [MY_LEG], [THEIR_LEG], the four `SolveReplayRef` kinds). A solve of
 * a puzzle names it by [day] (an ISO date) and [puzzleNumber] (null for a practice puzzle) for a daily one, or by
 * [puzzleId] for a friend's; a leg names its duel by [code] and [legIndex]. Build one with the factories, which fill
 * only the fields their kind reads.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.play.api.Replay")
data class ReplayKey(
    val gameId: String,
    val kind: String,
    val day: String? = null,
    val puzzleNumber: Int? = null,
    val puzzleId: String? = null,
    val code: String? = null,
    val legIndex: Int = 0,
) : NavKey {

    companion object {
        const val RECORD = "record"
        const val LATEST = "latest"
        const val MY_LEG = "myLeg"
        const val THEIR_LEG = "theirLeg"

        /** A daily puzzle's solve: its first win with [latest] false, its newest with [latest] true. */
        fun daily(gameId: String, day: String, puzzleNumber: Int?, latest: Boolean) =
            ReplayKey(gameId, if (latest) LATEST else RECORD, day = day, puzzleNumber = puzzleNumber)

        /** A friend's puzzle's solve, by its id. */
        fun custom(gameId: String, puzzleId: String, latest: Boolean) =
            ReplayKey(gameId, if (latest) LATEST else RECORD, puzzleId = puzzleId)

        /** Leg [legIndex] of the duel [code]: the player's own, or the opponent's when [theirs] (online only). */
        fun leg(gameId: String, code: String, legIndex: Int, theirs: Boolean = false) =
            ReplayKey(gameId, if (theirs) THEIR_LEG else MY_LEG, code = code, legIndex = legIndex)
    }
}
