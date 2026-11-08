package com.ivandsky.puzzlebox.play

import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import com.ivandsky.puzzlebox.play.friend.oneGoogleAccount
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Results page's sign-in is the friend board's: the page's button signs the player in through [FriendPuzzleBoard]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultViewModelSignInTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")

    @Test
    fun `sign in hands Google's token to the identity service`() {
        val fakes = FakeGames(listOf(alpha))
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val calendar: GameCalendar = FakeGameCalendar(day)
        val saved = FakeSavedPlayRepository()
        val dispatcher = main.testDispatcher
        val clock = Clock.fixed(day.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val history = ObserveGameHistory(fakes.games, daily, completions)
        val identity = FakeIdentityService()
        val store = PlaySessionStore(
            games = fakes.games,
            ui = fakes.registry,
            opening = SlotOpening(daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, WriteLane(), FakeReceivedPuzzleRepository()),
            writers = { SlotWriter(it, saved, WriteLane(), CoroutineScope(dispatcher)) },
            completions = completions,
            calendar = calendar,
            dispatchers = object : DispatcherProvider {
                override val main = dispatcher
                override val io = dispatcher
                override val default = dispatcher
            },
        )
        val viewModel = ResultViewModel(
            key = ResultKey(alpha.value, day.toString(), 90_000L, 0),
            store = store,
            todaySet = ObserveTodaySet(fakes.games, saved, completions, calendar, daily, SyncTodayPuzzles(fakes.games, daily, calendar)),
            calendar = calendar,
            history = history,
            stats = ObserveGameStats(fakes.games, history, ComputeStreak(GetPuzzleDay(fakes.games, clock))),
            rollover = GetNextRollover(fakes.games, clock),
            ui = fakes.registry,
            games = fakes.games,
            flags = FakeReleaseFlags(),
            insights = ObserveResultInsights(fakes.games, history, ComputeStreak(GetPuzzleDay(fakes.games, clock)), GetPuzzleDay(fakes.games, clock)),
            rank = ObserveResultRank(ObserveLeaderboard(identity, FakeLeaderboardRepository())),
            board = FriendPuzzleBoard(FakeCustomBoardRepository(), identity, oneGoogleAccount("token"), ComputeCustomBoard()),
        )

        viewModel.signIn(ApplicationProvider.getApplicationContext())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("token"), identity.signInCalls)
    }
}
