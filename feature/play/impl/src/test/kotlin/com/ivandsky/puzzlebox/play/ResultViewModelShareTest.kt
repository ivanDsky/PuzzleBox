package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.play.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.editor.Budget
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
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The view model's Share text ends with the link to the slot's day. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultViewModelShareTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val state = ResultUiState(game = "Alpha", puzzleNumber = 12)

    private fun viewModel(flags: ReleaseFlags): ResultViewModel {
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
            opening = SlotOpening(
                daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, WriteLane(), FakeReceivedPuzzleRepository(),
                FakeMatchRepository(),
                LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
                testDispatchers(dispatcher),
            ),
            writers = { SlotWriter(it, saved, WriteLane(), CoroutineScope(dispatcher)) },
            completions = completions,
            finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), identity) { Instant.EPOCH },
            calendar = calendar,
            dispatchers = object : DispatcherProvider {
                override val main = dispatcher
                override val io = dispatcher
                override val default = dispatcher
            },
        )
        return ResultViewModel(
            key = ResultKey(alpha.value, day.toString(), 90_000L, 0),
            store = store,
            todaySet = ObserveTodaySet(fakes.games, saved, completions, calendar, daily, SyncTodayPuzzles(fakes.games, daily, calendar)),
            calendar = calendar,
            history = history,
            rollover = GetNextRollover(fakes.games, clock),
            ui = fakes.registry,
            games = fakes.games,
            insights = ObserveResultInsights(fakes.games, history, ComputeStreak(GetPuzzleDay(fakes.games, clock)), GetPuzzleDay(fakes.games, clock)),
            rank = ObserveResultRank(ObserveLeaderboard(identity, FakeLeaderboardRepository(), fakes.games, calendar), fakes.games, friendDay = null),
            linkedIn = null,
            board = FriendPuzzleBoard(FakeCustomBoardRepository(), identity, oneGoogleAccount("token"), ComputeCustomBoard()),
            completions = completions,
            loadReplay = loadSolveReplay(fakes.games, daily, completions),
            releaseFlags = flags,
            nextThing = lazy { error("this test never reads the next thing") },
            daily = daily,
        )
    }

    @Test
    fun `the share text ends with the link to the result's day`() {
        assertEquals(
            "PuzzleBox Alpha #12 · 1:30 · no hints\nhttps://puzzlebox-f3356.web.app/p/${alpha.value}/2026-10-31",
            viewModel(FakeReleaseFlags()).shareText(state),
        )
    }
}
