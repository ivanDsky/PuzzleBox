package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
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

    private val clock = Clock.fixed(Instant.parse("${today}T12:00:00Z"), ZoneOffset.UTC)

    fun viewModel() = RanksViewModel(
        leaderboard = ObserveLeaderboard(this.identity, leaderboard, registry, calendar),
        observeRankWindow = ObserveRankWindow(this.identity, leaderboard, FakeFinalBoardStore(), registry, calendar, clock),
        observeOverall = ObserveOverall(this.identity, completions),
        identity = this.identity,
        idTokens = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: Context) = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
        },
        calendar = calendar,
        registry = registry,
        clock = clock,
    )
}
