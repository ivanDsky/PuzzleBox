package com.ivandsky.puzzlebox.app.screens

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.board.GameRow
import com.ivandsky.puzzlebox.profile.board.PerGameBoard

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
    /** The per-game board (A2): the selected game's board. */
    val perGameBoard: PerGameBoard,
) {
    sealed interface Account {
        /** The sign-in prompt; [signingIn] while the credential flow runs, then its outcome copy. */
        data class SignedOut(
            val signingIn: Boolean = false,
            val signInMessage: String? = null,
        ) : Account

        /** The player, signed in. */
        data class SignedIn(
            val identity: Identity,
        ) : Account
    }

    companion object {
        val NO_STATS = GameStats(
            currentStreak = 0,
            bestStreak = 0,
            solved = 0,
            hintFree = 0,
            averageTimeMs = null,
            playingSince = null,
            best = null,
            rankOrder = RankOrder.TimeThenHints,
        )
    }
}
