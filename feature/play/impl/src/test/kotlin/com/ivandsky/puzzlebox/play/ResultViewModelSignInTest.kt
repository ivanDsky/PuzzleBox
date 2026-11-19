package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
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
import com.ivandsky.puzzlebox.play.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import com.ivandsky.puzzlebox.play.friend.oneGoogleAccount
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Results page's sign-in is the friend board's: the page's button signs the player in through [FriendPuzzleBoard]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultViewModelSignInTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")

    private val identity = FakeIdentityService()

    private fun failingGoogle(reason: GoogleIdToken.Unavailable.Reason) = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Unavailable(reason)
    }

    private fun viewModel(google: GoogleIdTokenProvider): ResultViewModel {
        val fakes = FakeGames(listOf(alpha))
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val calendar: GameCalendar = FakeGameCalendar(day)
        val saved = FakeSavedPlayRepository()
        val dispatcher = main.testDispatcher
        val clock = Clock.fixed(day.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val history = ObserveGameHistory(fakes.games, daily, completions)
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
            finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), FakeIdentityService()) { Instant.EPOCH },
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
            rank = ObserveResultRank(ObserveLeaderboard(identity, FakeLeaderboardRepository(), fakes.games, calendar), fakes.games, friendDay = null), // no Friends/Global setting
            linkedIn = null,
            board = FriendPuzzleBoard(FakeCustomBoardRepository(), identity, google, ComputeCustomBoard()),
            completions = completions,
            loadReplay = loadSolveReplay(fakes.games, daily, completions),
            nextThing = lazy { error("this test never reads the next thing") },
            releaseFlags = FakeReleaseFlags(),
            daily = daily,
        )
    }

    private fun ResultViewModel.tapSignIn() {
        signIn(ApplicationProvider.getApplicationContext())
        main.testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `sign in hands Google's token to the identity service`() {
        val viewModel = viewModel(oneGoogleAccount("token"))

        viewModel.tapSignIn()

        assertEquals(listOf("token"), identity.signInCalls)
        assertNull(viewModel.signInNote.value)
    }

    @Test
    fun `a Google credential that cannot be had says so and leaves sign in ready to try again`() {
        val viewModel = viewModel(failingGoogle(GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE))

        viewModel.tapSignIn()

        assertEquals("Couldn't sign in. Try again.", viewModel.signInNote.value)
        assertEquals(emptyList<String>(), identity.signInCalls)
    }

    @Test
    fun `no network for Google says the player is offline`() {
        val viewModel = viewModel(failingGoogle(GoogleIdToken.Unavailable.Reason.NETWORK))

        viewModel.tapSignIn()

        assertEquals("You're offline. Sign in once you're back.", viewModel.signInNote.value)
    }

    @Test
    fun `a dismissed account picker shows no message`() {
        val viewModel = viewModel(failingGoogle(GoogleIdToken.Unavailable.Reason.CANCELLED))

        viewModel.tapSignIn()

        assertNull(viewModel.signInNote.value)
    }

    @Test
    fun `a message clears when the next try succeeds`() {
        var available = false
        val flaky = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: Context): GoogleIdToken =
                if (available) GoogleIdToken.Available("token") else GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE)
        }
        val viewModel = viewModel(flaky)
        viewModel.tapSignIn()
        assertEquals("Couldn't sign in. Try again.", viewModel.signInNote.value)

        available = true
        viewModel.tapSignIn()

        assertNull(viewModel.signInNote.value)
    }
}
