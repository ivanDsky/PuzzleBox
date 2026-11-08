package com.ivandsky.puzzlebox.app.screens

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
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
    /** The per-game board (A2); null while `PerGameLeaderboards` is off, which keeps the legacy board. */
    val perGameBoard: PerGameBoard? = null,
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

    /**
     * The flag-on board: [selected] is the game it shows (null while the default is still being
     * resolved, so [state] is Loading), ranked by [order]; [state] is the signed-in board or SignedOut.
     */
    data class PerGameBoard(
        val selected: GameId?,
        val order: RankOrder,
        val state: DailyBoardState,
    )

    /** One game's lifetime line, its [best] ranked by [rankOrder]; a tap opens History's timeline (N3). */
    data class GameRow(
        val gameId: GameId,
        val name: String,
        val brand: GameBrand,
        val solved: Int,
        val best: BestScore?,
        val rankOrder: RankOrder,
    )

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
