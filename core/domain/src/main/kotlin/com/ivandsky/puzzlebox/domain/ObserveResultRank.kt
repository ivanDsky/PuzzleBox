package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.ranks.FriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.game.linkedin.linkedInTimeWins
import com.ivandsky.puzzlebox.game.linkedin.rankCombined
import com.ivandsky.puzzlebox.game.linkedin.rankedCount
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
 *
 * With [friendDay] the rank follows the player's Friends/Global setting (F20): in Friends the position and count are
 * the player's place among their friends and connections, as the hub's Friends board has it ([FriendDay.standing]).
 * Global ranks exactly as above; with no setting to follow (the hub's flag off, or a graph built without
 * [friendDay]) the flow is the one above, untouched.
 */
class ObserveResultRank(
    private val leaderboard: ObserveLeaderboard,
    private val registry: GameRegistry,
    private val friendDay: ObserveFriendDay? = null,
) {

    operator fun invoke(game: GameId, day: LocalDate, connections: Flow<LinkedInDayView>? = null): Flow<ResultRank> {
        val order = registry.rankOrderOf(game)
        val boards = leaderboard(game, day)
        // Without connections the board alone drives the rank, so its loading state shows as it always did.
        val read: Flow<Pair<TodayLeaderboard, LinkedInDayView?>> = if (connections == null) boards.map { it to null } else combine(boards, connections, ::Pair)
        val scope = friendDay?.invoke(game, day)
        val ranks = if (scope == null) {
            read.map { (board, view) -> rankOn(board, view, friends = null, order) }
        } else {
            combine(read, scope) { (board, view), friends -> rankOn(board, view, friends, order) }
        }
        return ranks.distinctUntilChanged()
    }

    private fun rankOn(board: TodayLeaderboard, view: LinkedInDayView?, friends: FriendDay?, order: RankOrder): ResultRank {
        val signedIn = board as? TodayLeaderboard.SignedIn ?: return ResultRank.SignedOut
        val entries = (signedIn.board as? TodayLeaderboard.Board.Loaded)?.snapshot?.entries ?: return ResultRank.Hidden
        val uid = signedIn.identity.uid
        // In Friends the place already counts the connections, so there is no second place to carry.
        if (friends != null) return friends.standing(entries, uid, order)?.let { ranked(it.place, it.count, null) } ?: ResultRank.Hidden
        val index = entries.indexOfFirst { it.uid == uid }
        return if (index < 0) ResultRank.Hidden else ranked(index + 1, entries.size, withConnections(entries, index, view, order.comparator))
    }

    private fun ranked(position: Int, count: Int, withConnections: ResultRank.WithConnections?) = ResultRank.Ranked(
        position,
        count,
        topPercent = maxOf(1, (position * 100 + count - 1) / count),
        withConnections = withConnections,
    )

    // A connection with a hidden time has no score, so it takes no place and is not counted. A LinkedIn time wins:
    // the player's own import stands in for their PuzzleBox time, and a connection named like an entry is that
    // entry at the connection's time.
    private fun withConnections(
        entries: List<LeaderboardEntry>,
        own: Int,
        view: LinkedInDayView?,
        order: Comparator<BestScore>,
    ): ResultRank.WithConnections? {
        if (view == null) return null
        val ghosts = (view.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty()
        val entryScores = entries.map { it.score() }
        val wins = linkedInTimeWins(
            rowNames = entries.map { it.displayName },
            rowScores = entryScores,
            selfRow = own,
            ghostNames = ghosts.map { it.name },
            ghostScores = ghosts.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } },
            own = view.ownScore,
        )
        val kept = ghosts.filterIndexed { index, _ -> wins.ghostKept[index] }
        val keptScores = kept.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } }
        // Nothing from LinkedIn touches the board: the row is exactly the PuzzleBox rank alone.
        if (keptScores.all { it == null } && wins.rowScores == entryScores) return null
        val slots = rankCombined(wins.rowScores, keptScores, order)
        val mine = slots.single { !it.ghost && it.index == own }
        return ResultRank.WithConnections(position = checkNotNull(mine.place), count = slots.rankedCount())
    }

    private fun LeaderboardEntry.score() = BestScore(timeMs, hints)
}
