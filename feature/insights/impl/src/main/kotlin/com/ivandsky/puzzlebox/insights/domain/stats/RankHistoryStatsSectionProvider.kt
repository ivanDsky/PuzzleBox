package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.domain.guardedAsk
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsOverallRead
import com.ivandsky.puzzlebox.insights.domain.recap.ResolveFriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.settledEach
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The rank history section: the viewer's Friends points per kept week of one game, read from the weeks this device
 * kept ([rankHistory], the one reader). It ignores the stats range: the weeks are always weekly and every one is shown,
 * so [readKey] is the game asked and nothing else, and switching the range neither re-reads nor changes it. It is per
 * game only: the weeks kept differ by game, so no one line could stand for all games. The all-games page leaves the
 * section out (null), and its unlock line is vs friends'.
 *
 * - Signed out: [RankHistoryStanding.Locked], which the screen draws as one plain line in the section's place.
 * - Weeks kept: they show at once, as they are on this device, offline too. A kept week is never changed (A19).
 * - Signed in with nothing kept: [SectionState.Loading] while Friends Overall for the current week says whether there
 *   are friends. Nobody but the viewer on the boards: [RankHistoryStanding.Locked] asking to add friends. Friends, or no board
 *   yet: no weeks yet, which the screen says plainly. A read that is partial or offline, fails or takes longer than
 *   [timeout]: [SectionState.Unavailable] (A16), and the rest of the page is unaffected.
 *
 * Signing in or out while the page is open reads again. Kept weeks are read once per account, so a week kept while the
 * page is open shows on the next open.
 */
internal class RankHistoryStatsSectionProvider(
    private val identity: IdentityService,
    private val rankHistory: (GameId) -> Flow<List<RankHistoryStore.Week>>,
    private val friendsOverall: FriendsOverallRead,
    private val failures: FailureLog = LogcatFailureLog,
    private val timeout: Duration = ResolveFriendsStanding.READ_TIMEOUT,
) : StatsSectionProvider {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun section(input: StatsInput): Flow<StatsSection?> {
        val game = input.facts.scope ?: return flow { emit(null) }
        return identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { viewer -> read(input, game, viewer) }
    }

    /** The weeks are the game's own, whatever the range or the facts. */
    override fun readKey(input: StatsInput): Any = input.facts.scope ?: AllGames

    private fun read(input: StatsInput, game: GameId, viewer: String?): Flow<StatsSection?> = flow {
        if (viewer == null) {
            emit(ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.SignIn)))
            return@flow
        }
        val kept = guardedAsk(failures, TAG, "kept rank history") { rankHistory(game).first() }
        when {
            kept == null -> emit(StatsSection.RankHistory(SectionState.Unavailable))
            kept.isNotEmpty() -> emit(ready(RankHistoryStanding.Kept.of(kept)))
            else -> {
                emit(StatsSection.RankHistory(SectionState.Loading))
                emit(withoutWeeks(input.today, game, viewer))
            }
        }
    }

    /** Nothing is kept: whether there are friends decides between the unlock line, no weeks yet and not loading right now. */
    private suspend fun withoutWeeks(today: LocalDate, game: GameId, viewer: String): StatsSection {
        val states = guardedAsk(failures, TAG, "rank history friends", timeout) {
            friendsOverall.settledEach(StatsRange.Week.friendsWindow(today), listOf(game)).map { it.second }
        }
        return when (states?.friendsPresence(viewer)) {
            FriendsPresence.SignedOut -> ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.SignIn))
            FriendsPresence.Alone -> ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.AddFriends))
            FriendsPresence.NoBoards, FriendsPresence.WithFriends -> ready(RankHistoryStanding.Kept(emptyList()))
            FriendsPresence.Unreadable, null -> StatsSection.RankHistory(SectionState.Unavailable)
        }
    }

    private fun ready(history: RankHistoryStanding) = StatsSection.RankHistory(SectionState.Ready(history))

    /** The read key of the all-games page, which has no section. */
    private object AllGames

    private companion object {
        const val TAG = "RankHistoryStats"
    }
}
