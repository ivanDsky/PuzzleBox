package com.ivandsky.puzzlebox.app.screens

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode

/**
 * Profile (A20): the player's record — lifetime [stats] with [daysPlayed], one [games] row
 * per registered game — then the [account] (Google sign-in, A6, and today's board) and the
 * app's [themeMode] (Appearance, N4). Signed out is a first-class, clearly-prompted state
 * (A7/A15) — nothing else in the app is gated on identity.
 */
data class ProfileUiState(
    val account: Account = Account.SignedOut(),
    val stats: GameStats = NO_STATS,
    /** Distinct days with at least one solved daily (practice never counts). */
    val daysPlayed: Int = 0,
    val games: List<GameRow> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    sealed interface Account {
        /** The sign-in prompt; [signingIn] while the credential flow runs, then its outcome copy. */
        data class SignedOut(
            val signingIn: Boolean = false,
            val signInMessage: String? = null,
        ) : Account

        /** The player and today's board ([TodayLeaderboard.Board]: loading, loaded, or unavailable). */
        data class SignedIn(
            val identity: Identity,
            val board: TodayLeaderboard.Board,
        ) : Account
    }

    /** One game's lifetime line; a tap opens that game's History tab (N3). */
    data class GameRow(
        val gameId: GameId,
        val name: String,
        val brand: GameBrand,
        val solved: Int,
        val bestTimeMs: Long?,
    )

    companion object {
        val NO_STATS = GameStats(
            currentStreak = 0,
            bestStreak = 0,
            solved = 0,
            bestTimeMs = null,
            hintFree = 0,
            averageTimeMs = null,
            playingSince = null,
        )
    }
}
