package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Derives the [ResultRank] of the signed-in player on [game]'s board for [day] from
 * [ObserveLeaderboard]. Entries already come sorted by the board's order, so the player's
 * list position is the rank; the player is matched by identity uid. Posts nothing.
 *
 * With [connections] (a day's LinkedIn view, read from the phone only) the rank also carries the player's place among
 * the PuzzleBox entries and the connections with a time, ranked by the game's own order.
 */
class ObserveResultRank(private val leaderboard: ObserveLeaderboard, private val registry: GameRegistry) {

    operator fun invoke(game: GameId, day: LocalDate, connections: Flow<LinkedInDayView>? = null): Flow<ResultRank> {
        val order = registry.rankOrderOf(game).comparator
        val boards = leaderboard(game, day)
        // Without connections the board alone drives the rank, so its loading state shows as it always did.
        val read = if (connections == null) {
            boards.map { it to emptyList<LinkedInGhost>() }
        } else {
            combine(boards, connections.map { view -> (view.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty() }, ::Pair)
        }
        return read.map { (board, ghosts) ->
            when (board) {
                TodayLeaderboard.SignedOut -> ResultRank.SignedOut
                is TodayLeaderboard.SignedIn -> when (val state = board.board) {
                    TodayLeaderboard.Board.Loading, TodayLeaderboard.Board.Unavailable -> ResultRank.Hidden
                    is TodayLeaderboard.Board.Loaded -> {
                        val entries = state.snapshot.entries
                        val index = entries.indexOfFirst { it.uid == board.identity.uid }
                        if (index < 0) ResultRank.Hidden else ranked(index + 1, entries.size, withConnections(entries, index, ghosts, order))
                    }
                }
            }
        }.distinctUntilChanged()
    }

    private fun ranked(position: Int, count: Int, withConnections: ResultRank.WithConnections?) = ResultRank.Ranked(
        position,
        count,
        topPercent = maxOf(1, (position * 100 + count - 1) / count),
        withConnections = withConnections,
    )

    // A connection with a hidden time has no score, so it takes no place and is not counted.
    private fun withConnections(
        entries: List<LeaderboardEntry>,
        own: Int,
        ghosts: List<LinkedInGhost>,
        order: Comparator<BestScore>,
    ): ResultRank.WithConnections? {
        val scores = ghosts.mapNotNull { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } }
        if (scores.isEmpty()) return null
        val mine = entries[own].score()
        val ahead = entries.count { order.compare(it.score(), mine) < 0 } + scores.count { order.compare(it, mine) < 0 }
        return ResultRank.WithConnections(position = ahead + 1, count = entries.size + scores.size)
    }

    private fun LeaderboardEntry.score() = BestScore(timeMs, hints)
}
