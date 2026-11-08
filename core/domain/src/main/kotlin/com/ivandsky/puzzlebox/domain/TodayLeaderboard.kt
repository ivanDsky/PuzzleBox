package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot

/** Today's leaderboard as the signed-in player sees it (ticket 16; A6/A7). */
sealed interface TodayLeaderboard {
    /** Nothing is gated on identity, so no board is observed while signed out (A7/A15). */
    data object SignedOut : TodayLeaderboard

    data class SignedIn(val identity: Identity, val board: Board) : TodayLeaderboard

    sealed interface Board {
        /** Signed in, first snapshot not delivered yet. */
        data object Loading : Board

        data class Loaded(val snapshot: LeaderboardSnapshot) : Board

        /** A7: the backend can't serve the board right now — a soft state, never an error. */
        data object Unavailable : Board
    }
}
