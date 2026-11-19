package com.ivandsky.puzzlebox.domain.insights.rankhistory

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.isFinal
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.days
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Keeps the signed-in player's weekly Friends rank on this device, one [RankHistoryStore.Week] per game and week, so
 * the Insights stats page can chart it without asking the cloud again.
 *
 * It follows the account: [invoke] suspends for as long as it is collected, runs one pass for each signed-in account
 * that has friends, and starts over when the account changes. Signed out, or with no friends, it reads nothing.
 *
 * A pass looks only at weeks the player solved in this app (a solve imported from elsewhere has no leaderboard entry),
 * that are final, and that fall within the last [MAX_WEEKS] final weeks. That bound is the cap on what a new device
 * refills from the cloud, and it holds on every run. A week already kept is skipped without any read. Each remaining
 * week is read one at a time through [ObserveFriendsOverall] and kept only when every board of it loaded; a partial or
 * offline read, or one where the player has no row, keeps nothing and is tried again on the next run. A kept week is
 * never rewritten, so a friend who joins later does not change it.
 */
class SyncRankHistory(
    private val identity: IdentityService,
    private val observeFriendUids: ObserveFriendUids,
    private val observeGameHistory: ObserveGameHistory,
    private val store: RankHistoryStore,
    private val friendsOverall: ObserveFriendsOverall,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Follows the account until the caller cancels; cancelling stops any read in flight. */
    suspend operator fun invoke() {
        combine(
            identity.current.map { it?.uid },
            observeFriendUids().map { it.isNotEmpty() },
        ) { uid, hasFriends -> uid.takeIf { hasFriends } }
            .distinctUntilChanged()
            .collectLatest { uid -> if (uid != null) fill(uid) }
    }

    private suspend fun fill(ownerUid: String) {
        val utcToday = LocalDate.now(clock.withZone(ZoneOffset.UTC))
        val due = playedWeeks()
            .flatMap { (game, played) -> dueWeeks(ownerUid, game, played, utcToday) }
            .sortedWith(compareByDescending<Due> { it.weekStart }.thenBy { it.game.value })
        due.forEach { keep(ownerUid, it) }
    }

    /** The weeks of each game with a solve made in this app, by the puzzle's day. */
    private suspend fun playedWeeks(): Map<GameId, Set<LocalDate>> =
        observeGameHistory().first()
            .filter { it.record?.source == CompletionSource.App }
            .groupBy({ it.gameId }, { mondayOf(it.day) })
            .mapValues { (_, weeks) -> weeks.toSet() }

    /** The played final weeks within the horizon that are not kept yet. */
    private suspend fun dueWeeks(
        ownerUid: String,
        game: GameId,
        played: Set<LocalDate>,
        utcToday: LocalDate,
    ): List<Due> {
        val candidates = lastFinalWeeks(game, utcToday).filter { it in played }
        if (candidates.isEmpty()) return emptyList()
        val kept = store.weeks(ownerUid, game, candidates.min()..candidates.max()).mapTo(HashSet()) { it.weekStart }
        return candidates.filter { it !in kept }.map { Due(game, it) }
    }

    /** Reads one week and keeps it when it is whole; any other outcome keeps nothing. */
    private suspend fun keep(ownerUid: String, due: Due) {
        val read = friendsOverall(RankWindow.Week(due.weekStart), listOf(due.game))
            .firstOrNull { it !is OverallState.Loading && it !is OverallState.Partial }
        val standings = (read as? OverallState.Complete)?.standings ?: return
        val own = standings.rows.firstOrNull { it.uid == ownerUid }?.takeIf { it.games > 0 } ?: return
        store.insertOnce(
            RankHistoryStore.Week(
                ownerUid = ownerUid,
                game = due.game,
                weekStart = due.weekStart,
                pointsPerDay = own.total / own.games,
                friendCount = standings.rows.size - 1,
            ),
        )
    }

    private data class Due(val game: GameId, val weekStart: LocalDate)

    private companion object {
        /** The most final weeks a pass looks back over, newest first. */
        const val MAX_WEEKS = 8

        /** The Monday of the week holding [day]. */
        fun mondayOf(day: LocalDate): LocalDate = RankWindow.Week(day).days(day.plusDays(6)).first()

        /** The Mondays of the newest [MAX_WEEKS] weeks of [game] whose last board is final, newest first. */
        fun lastFinalWeeks(game: GameId, utcToday: LocalDate): List<LocalDate> =
            generateSequence(mondayOf(utcToday)) { it.minusWeeks(1) }
                .filter { LeaderboardRef(game.value, it.plusDays(6)).isFinal(utcToday) }
                .take(MAX_WEEKS)
                .toList()
    }
}
