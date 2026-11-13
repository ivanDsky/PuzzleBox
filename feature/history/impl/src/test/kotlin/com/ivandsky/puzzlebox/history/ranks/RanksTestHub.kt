package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
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
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The hub's view model over fakes the test keeps hold of: counting leaderboard, settable identity and calendar. */
internal class RanksTestHub(
    val leaderboard: FakeLeaderboardRepository = FakeLeaderboardRepository(),
    identity: Identity? = Identity("u1", "Ann", null),
    val registry: GameRegistry = HistoryTestSupport.registry,
    val today: LocalDate = LocalDate.of(2026, 11, 13),
) {
    val identity = FakeIdentityService(initial = identity)
    val calendar = FakeGameCalendar(today)
    val completions = FakeCompletionRepository()
    val preferences = FakeUserPreferencesRepository()
    val daily = FakeDailyRepository()
    /** What the phone holds of the viewer's LinkedIn days; read only by a [viewModel] built with LinkedIn on. */
    val linkedInDays = FakeLinkedInDays()
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

    private val clock = Clock.fixed(Instant.parse("${today}T12:00:00Z"), ZoneOffset.UTC)

    fun viewModel(linkedIn: Boolean = false): RanksViewModel {
        val history = ObserveGameHistory(registry, daily, completions)
        return RanksViewModel(
            observeRankWindow = ObserveRankWindow(this.identity, leaderboard, FakeFinalBoardStore(), registry, calendar, clock),
            observeOverall = ObserveOverall(this.identity, completions),
            observeFriendUids = ObserveFriendUids(matches, this.identity),
            observeGameHistory = history,
            observeGameStats = ObserveGameStats(registry, history, ComputeStreak(puzzleDay = GetPuzzleDay(registry, clock))),
            playRegistry = GamePlayRegistry(emptyList()),
            loadReplay = HistoryTestSupport.loadReplay(FakeReleaseFlags(), registry, daily, completions),
            identity = this.identity,
            idTokens = object : GoogleIdTokenProvider {
                override suspend fun idToken(activityContext: Context) = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
            },
            preferences = preferences,
            calendar = calendar,
            registry = registry,
            clock = clock,
            linkedIn = ObserveLinkedInDayView(ObserveLinkedInDay(linkedInDays, registry), SyncLinkedInDay(linkedInDays, 500), FakeLinkedInLink(), linkedInDays).takeIf { linkedIn },
        )
    }
}
