package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.LoadFriendsPastPage
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendBoards
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.history.domain.ranks.ObserveWindowRefresh
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.flags.HistoryFlag
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Clock
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onStart

/** The hub's view model over fakes the test keeps hold of: counting leaderboard, settable identity and calendar. */
internal class RanksTestHub(
    val leaderboard: FakeLeaderboardRepository = FakeLeaderboardRepository(),
    identity: Identity? = Identity("u1", "Ann", null),
    val registry: GameRegistry = HistoryTestSupport.registry,
    val today: LocalDate = LocalDate.of(2026, 11, 13),
    /** The player's stored default scope: Global here, so a test names Friends when it means it. */
    friendsDefault: Boolean = false,
    /** The player's stored default games: All games here, so a test names LinkedIn games when it means it. */
    linkedInDefault: Boolean = false,
    /** When set, the stored default does not arrive until it completes, like a DataStore read that has not returned yet. */
    private val storedDefaultGate: CompletableDeferred<Unit>? = null,
) {
    val identity = FakeIdentityService(initial = identity)
    val calendar = FakeGameCalendar(today)
    val completions = FakeCompletionRepository()
    val preferences = FakeUserPreferencesRepository(ranksFriendsDefault = friendsDefault, ranksLinkedInDefault = linkedInDefault)
    val daily = FakeDailyRepository()
    /** What the phone holds of the viewer's LinkedIn days; read only by a [viewModel] built with LinkedIn on. */
    val linkedInDays = FakeLinkedInDays()
    /** The connection the hub's reconnect refresh hears; online until a test says otherwise. */
    val network = FakeNetworkStatus()
    val matches = FakeMatchRepository().apply { signedInUid = identity?.uid }

    /** A duel between the viewer and [uid], which makes [uid] a friend. */
    suspend fun befriend(uid: String, code: String = "AAAAA2") {
        val me = checkNotNull(this.identity.currentIdentity)
        val shareCode = checkNotNull(ShareCode.parse(code))
        val leg = MatchLeg(LegRef.Daily(GameId("zip"), today, 1), null)
        matches.create(MatchDraft(shareCode, 24, Player(me.uid, me.displayName, null), null, listOf(leg), true, null))
        matches.signedInUid = uid
        matches.claim(shareCode, Player(uid, uid, null))
        matches.signedInUid = me.uid
    }

    /** The instant the hub's clock reads: noon of [today] until a test moves it with [advanceClock]. */
    var now: Instant = Instant.parse("${today}T12:00:00Z")
        private set

    fun advanceClock(by: Duration) {
        now += by
    }

    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant(): Instant = now
    }

    /** The phone's LinkedIn days as the hub reads them with LinkedIn on. */
    private fun linkedInView() = ObserveLinkedInDayView(ObserveLinkedInDay(linkedInDays, registry), SyncLinkedInDay(linkedInDays, 500), FakeLinkedInLink(), linkedInDays)

    /** The domain's Friends boards as domainModule binds them (LinkedIn on): a hub built with LinkedIn on and [friendsOverall] share this one. */
    private val linkedInFriendBoards = ObserveFriendBoards(ObserveFriendUids(matches, this.identity), this.identity, completions, linkedInView())

    /** The domain's Friends Overall over the same fakes, clock and Friends boards as [viewModel] built with LinkedIn on: a second caller of the hub's pipeline. */
    fun friendsOverall() = ObserveFriendsOverall(
        ObserveRankWindow(this.identity, leaderboard, FakeFinalBoardStore(), registry, calendar, clock),
        linkedInFriendBoards,
        ObserveOverall(this.identity, completions),
    )

    fun viewModel(
        linkedIn: Boolean = false,
        releaseFlags: ReleaseFlags = FakeReleaseFlags(),
        savedState: SavedStateHandle = SavedStateHandle(),
        opening: RanksSelection = RanksSelection(),
    ): RanksViewModel {
        val history = ObserveGameHistory(registry, daily, completions)
        return RanksViewModel(
            observeRankWindow = ObserveRankWindow(this.identity, leaderboard, FakeFinalBoardStore(), registry, calendar, clock),
            observeWindowRefresh = ObserveWindowRefresh(network),
            observeOverall = ObserveOverall(this.identity, completions),
            observeFriendUids = ObserveFriendUids(matches, this.identity),
            observeGameHistory = history,
            observeGameStats = ObserveGameStats(registry, history, ComputeStreak(puzzleDay = GetPuzzleDay(registry, clock))),
            playRegistry = GamePlayRegistry(emptyList()),
            loadReplay = HistoryTestSupport.loadReplay(registry, daily, completions),
            identity = this.identity,
            idTokens = object : GoogleIdTokenProvider {
                override suspend fun idToken(activityContext: Context) = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
            },
            preferences = object : UserPreferencesRepository by preferences {
                override val ranksFriendsDefault = preferences.ranksFriendsDefault.onStart { storedDefaultGate?.await() }
            },
            calendar = calendar,
            registry = registry,
            daily = daily,
            clock = clock,
            // As historyModule wires it: off, the same boards count no connection; with FriendsPastGlobalPage on they also read the friends a full page leaves out.
            friendBoards = when {
                releaseFlags.isEnabled(HistoryFlag.FriendsPastGlobalPage) ->
                    ObserveFriendBoards(ObserveFriendUids(matches, this.identity), this.identity, completions, linkedInView().takeIf { linkedIn }, LoadFriendsPastPage(leaderboard))
                linkedIn -> linkedInFriendBoards
                else -> ObserveFriendBoards(ObserveFriendUids(matches, this.identity), this.identity, completions, linkedIn = null)
            },
            linkedIn = linkedInView().takeIf { linkedIn },
            releaseFlags = releaseFlags,
            savedState = savedState,
            opening = opening,
        )
    }
}
