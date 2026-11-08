package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Identity-gated observation of one game's board for one day: every identity change
 * restarts the board (a new player never sees the previous one's snapshot), emitting
 * [TodayLeaderboard.Board.Loading] until the first snapshot; a null snapshot is
 * [TodayLeaderboard.Board.Unavailable] (A7). Signed out observes nothing (A7/A15).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLeaderboard(
    private val identity: IdentityService,
    private val leaderboard: LeaderboardRepository,
    private val registry: GameRegistry,
) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<TodayLeaderboard> = observe({ game }, { day })

    /** The board's game and day are read each time it (re)starts, so a restart after a rollover follows the new day. */
    internal fun observe(game: () -> GameId, day: () -> LocalDate): Flow<TodayLeaderboard> =
        identity.current.flatMapLatest { player ->
            if (player == null) {
                flowOf(TodayLeaderboard.SignedOut)
            } else {
                val id = game()
                leaderboard.observe(LeaderboardRef(id.value, day()), registry.rankOrderOf(id))
                    .map { snapshot ->
                        TodayLeaderboard.SignedIn(
                            identity = player,
                            board = snapshot?.let(TodayLeaderboard.Board::Loaded) ?: TodayLeaderboard.Board.Unavailable,
                        )
                    }
                    .onStart { emit(TodayLeaderboard.SignedIn(player, TodayLeaderboard.Board.Loading)) }
            }
        }
}
