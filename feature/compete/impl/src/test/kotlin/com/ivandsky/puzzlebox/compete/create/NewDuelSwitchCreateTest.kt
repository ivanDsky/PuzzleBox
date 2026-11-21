package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.compete.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.compete.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ValidateExactPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** New duel: a Create in flight belongs to the opponent it started for, so switching opponent never moves its outcome onto the other pane. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewDuelSwitchCreateTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 9)
    // gamma has no archive, so an "Any gamma" row can never be drawn.
    private val gamma = GameId("gamma")
    private val registry = TestGames.registry(listOf(alpha, beta, gamma))
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
        }
    }
    private val completions = FakeCompletionRepository()
    private var gate = CompletableDeferred(Unit)

    /** Completions whose every read waits for [gate]: a Create that reads them is suspended while the gate is shut. */
    private val gated = object : CompletionRepository by completions {
        override fun observeRecords(): Flow<List<CompletionRecord>> = completions.observeRecords().onStart { gate.await() }
    }
    private val received = FakeReceivedPuzzleRepository()
    private val identity = FakeIdentityService(ANN)
    private val server = FakeMatchRepository().apply { signedInUid = ANN.uid }
    private val local = FakeMatchLocalRepository()
    private val self = FakeSelfMatchRepository()
    private val pool = ComputeSolvedPool(registry, gated, received)
    private val idTokens = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("t")
    }

    private fun TestScope.started(): NewDuelViewModel =
        NewDuelViewModel(
            createMatch = CreateMatch(
                server, local, ComputeMatchLegs(registry, daily, calendar, ObserveGameHistory(registry, daily, gated), received, Random(DRAW_SEED)),
                identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository()),
            ),
            identity = identity,
            idTokens = idTokens,
            todaySet = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, calendar, daily, SyncTodayPuzzles(registry, daily, calendar)),
            calendar = calendar,
            daily = daily,
            registry = registry,
            createSelfDuel = CreateSelfDuel(pool, completions, received, self, local, { NOW }, Random(DRAW_SEED)),
            computeSolvedPool = pool,
            clock = { NOW },
            zone = { ZoneOffset.UTC },
            locale = { Locale.US },
            legTitles = noLegTitles(),
        ).also { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} } }

    private fun solve(game: GameId, vararg days: Int) = completions.setRecords(
        days.map { day ->
            CompletionRecord(
                puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(LocalDate.of(2026, 10, day), puzzleNumber = day)),
                time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
            )
        },
    )

    @Test
    fun `a Past you Create with no puzzles left, finished after a switch to Friend, leaves no notice on the friend pane`() = runTest {
        solve(alpha, 1, 2, 3, 4)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)
        gate = CompletableDeferred()
        vm.create()
        vm.chooseOpponent(Opponent.Friend)
        assertTrue(vm.state.value.creating)
        completions.setRecords(emptyList())

        gate.complete(Unit)

        assertNull(vm.state.value.notice)
        assertFalse(vm.state.value.creating)
        vm.chooseOpponent(Opponent.PastYou)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `a friend Create with an undrawable row, finished after a switch to Past you, marks no row and shows no notice`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.editLineup { it.openChange(1) }
        vm.editLineup { it.pick(AnyGame(gamma)) }
        gate = CompletableDeferred()
        vm.create()
        vm.chooseOpponent(Opponent.PastYou)
        assertTrue(vm.state.value.creating)

        gate.complete(Unit)

        assertNull(vm.state.value.notice)
        assertFalse(vm.state.value.creating)
        vm.chooseOpponent(Opponent.Friend)
        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.lineup.marked)
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun `a Past you duel created while the player switched to Friend still opens`() = runTest {
        solve(alpha, 1, 2, 3, 4)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)
        gate = CompletableDeferred()
        vm.create()
        vm.chooseOpponent(Opponent.Friend)

        gate.complete(Unit)

        assertEquals(self.observeAll().first().single().code, vm.opened.first())
        assertNull(vm.state.value.notice)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
