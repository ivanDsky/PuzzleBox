package com.ivandsky.puzzlebox.app.screens

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.TodayLeaderboard

/**
 * Profile (ticket 16): Google sign-in state (A6) plus the per-day leaderboard v1.
 * Signed out is a first-class, clearly-prompted state (A7/A15) — nothing else in the
 * app is gated on identity.
 */
sealed interface ProfileUiState {
    /** The sign-in prompt; [signingIn] while the credential flow runs, then its outcome copy. */
    data class SignedOut(
        val signingIn: Boolean = false,
        val signInMessage: String? = null,
    ) : ProfileUiState

    /** The player and today's board ([TodayLeaderboard.Board]: loading, loaded, or unavailable). */
    data class SignedIn(
        val identity: Identity,
        val board: TodayLeaderboard.Board,
    ) : ProfileUiState
}
