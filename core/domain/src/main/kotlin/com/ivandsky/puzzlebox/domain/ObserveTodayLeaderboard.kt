package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

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

/**
 * Identity-gated observation of today's board for the featured game: every identity
 * change restarts the board (a new player never sees the previous one's snapshot),
 * emitting [TodayLeaderboard.Board.Loading] until the first snapshot; a null snapshot is
 * [TodayLeaderboard.Board.Unavailable] (A7). [today] is read when the board (re)starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTodayLeaderboard(
    private val identity: IdentityService,
    private val leaderboard: LeaderboardRepository,
    private val featured: GetFeaturedGame,
    private val today: () -> LocalDate,
) {

    operator fun invoke(): Flow<TodayLeaderboard> = identity.current.flatMapLatest { player ->
        if (player == null) {
            flowOf(TodayLeaderboard.SignedOut)
        } else {
            leaderboard.observe(LeaderboardRef(featured().id.value, today()))
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
