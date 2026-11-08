package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Derives the [ResultRank] of the signed-in player on [game]'s board for [day] from
 * [ObserveLeaderboard]. Entries already come sorted by the board's order, so the player's
 * list position is the rank; the player is matched by identity uid. Posts nothing.
 */
class ObserveResultRank(private val leaderboard: ObserveLeaderboard) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<ResultRank> =
        leaderboard(game, day).map { board ->
            when (board) {
                TodayLeaderboard.SignedOut -> ResultRank.SignedOut
                is TodayLeaderboard.SignedIn -> when (val state = board.board) {
                    TodayLeaderboard.Board.Loading, TodayLeaderboard.Board.Unavailable -> ResultRank.Hidden
                    is TodayLeaderboard.Board.Loaded -> {
                        val entries = state.snapshot.entries
                        val index = entries.indexOfFirst { it.uid == board.identity.uid }
                        if (index < 0) ResultRank.Hidden else ranked(index + 1, entries.size)
                    }
                }
            }
        }.distinctUntilChanged()

    private fun ranked(position: Int, count: Int) =
        ResultRank.Ranked(position, count, topPercent = maxOf(1, (position * 100 + count - 1) / count))
}
