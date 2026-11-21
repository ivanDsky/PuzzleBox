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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The New duel sheet's "Play against" choice and its Past you branch, over the core:testing fakes and the real use cases. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewDuelSelfViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 9)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
        }
    }
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val identity = FakeIdentityService(ANN)
    private val server = FakeMatchRepository().apply { signedInUid = ANN.uid }
    private val local = FakeMatchLocalRepository()
    private val self = FakeSelfMatchRepository()
    private var poolThrows = false
    private var gate = CompletableDeferred(Unit)
    private val pool = ComputeSolvedPool(
        registry,
        object : CompletionRepository by completions {
            override fun observeRecords(): Flow<List<CompletionRecord>> =
                if (poolThrows) flow { throw IllegalStateException("room closed") } else completions.observeRecords().onStart { gate.await() }
        },
        received,
    )
    private val noToken = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("t")
    }

    private fun TestScope.started(): NewDuelViewModel {
        val history = ObserveGameHistory(registry, daily, completions)
        val vm = NewDuelViewModel(
            createMatch = CreateMatch(server, local, ComputeMatchLegs(registry, daily, calendar, history, received, Random(DRAW_SEED)), identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())),
            identity = identity,
            idTokens = noToken,
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
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun solve(game: GameId, vararg days: Int) = completions.setRecords(
        days.map { day ->
            CompletionRecord(
                puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(LocalDate.of(2026, 10, day), puzzleNumber = day)),
                time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
            )
        },
    )

    @Test
    fun `past you creates a self duel and opens it by its code, with no friend duel made`() = runTest {
        solve(alpha, 1, 2, 3, 4)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)

        vm.create()

        val code = vm.opened.first()
        val match = self.observeAll().first().single()
        assertEquals(code, match.code)
        assertEquals(3, match.legs.size)
        assertTrue(server.matches.isEmpty())
        assertEquals("Best of 3 · mixed solved puzzles · against your first solves", vm.state.value.summary)
        assertNull(withTimeoutOrNull(1) { vm.created.first() })
    }

    @Test
    fun `rounds stop at the solved puzzles and Create plays that many`() = runTest {
        solve(alpha, 1, 2)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)
        val view = vm.state.value.self
        assertEquals(2, view.cap)
        assertEquals(2, view.rounds)
        assertTrue(vm.state.value.summary, vm.state.value.summary.startsWith("Best of 2 · "))

        vm.create()
        vm.opened.first()

        assertEquals(2, self.observeAll().first().single().legs.size)
    }

    @Test
    fun `a puzzle solved after the sheet first opened is counted when it opens again`() = runTest {
        solve(alpha, 1, 2)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)
        vm.refreshSolved()
        assertEquals(2, vm.state.value.self.cap)

        solve(alpha, 1, 2, 3)
        vm.refreshSolved()

        assertEquals(3, vm.state.value.self.cap)
    }

    @Test
    fun `friends' puzzles switch re-reads the pool`() = runTest {
        solve(alpha, 1, 2)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)
        assertEquals(2, vm.state.value.self.cap)

        vm.setFriends(true)

        assertTrue(vm.state.value.self.form.includeFriends)
        assertEquals(DuelPreset.Custom, vm.state.value.self.form.preset)
        assertEquals(2, vm.state.value.self.cap)
    }

    @Test
    fun `no solved puzzle turns Create off with its reason`() = runTest {
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)

        val state = vm.state.value
        assertTrue(state.self.empty)
        assertEquals(0, state.self.rounds)
        assertFalse(state.canCreate)
        vm.create()
        assertTrue(self.observeAll().first().isEmpty())
        assertFalse(vm.state.value.creating)
        assertNull(withTimeoutOrNull(1) { vm.opened.first() })
    }

    @Test
    fun `signed out, past you still creates and no sign-in step shows`() = runTest {
        solve(alpha, 1, 2, 3)
        identity.currentIdentity = null
        val vm = started()
        assertEquals(DuelStep.SignIn, vm.state.value.step)
        assertTrue(vm.state.value.selfOffered)

        vm.chooseOpponent(Opponent.PastYou)

        assertEquals(DuelStep.Create, vm.state.value.step)
        assertTrue(vm.state.value.canCreate)
        vm.create()
        vm.opened.first()
        assertEquals(1, self.observeAll().first().size)
        assertEquals("You", vm.state.value.selfName)
    }

    @Test
    fun `a challenge names a friend already, so past you is not offered`() = runTest {
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)

        vm.prefill(DuelChallenge("sam", "Sam", beta))

        assertFalse(vm.state.value.selfOffered)
        assertEquals(Opponent.Friend, vm.state.value.opponent)
    }

    @Test
    fun `each opponent keeps its own choices, and Daily and deadline edits leave past you untouched`() = runTest {
        solve(alpha, 1, 2, 3, 4, 5)
        val vm = started()
        val friendBefore = vm.state.value.draft
        vm.chooseOpponent(Opponent.PastYou)
        val before = vm.state.value.self.form

        vm.setDeadline(48)
        vm.chooseDaily()
        vm.toggle(vm.state.value.games.first { it.id == alpha })

        assertEquals(before, vm.state.value.self.form)
        vm.chooseOpponent(Opponent.Friend)
        assertEquals(friendBefore, vm.state.value.draft)
    }

    @Test
    fun `a Room throw while creating against past you is the failed notice, not a crash`() = runTest {
        solve(alpha, 1, 2, 3, 4)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)
        poolThrows = true

        vm.create()

        assertTrue(vm.state.value.notice is DuelNotice.Failed)
        assertFalse(vm.state.value.creating)
    }

    @Test
    fun `a Room throw reading the solved pool leaves the sheet open with no counts`() = runTest {
        solve(alpha, 1, 2, 3, 4)
        poolThrows = true

        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)

        assertEquals(Opponent.PastYou, vm.state.value.opponent)
    }

    @Test
    fun `closing the sheet mid-create still delivers the Past you duel it made`() = runTest {
        solve(alpha, 1, 2, 3, 4)
        val vm = started()
        vm.chooseOpponent(Opponent.PastYou)
        gate = CompletableDeferred()
        vm.create()
        vm.reset()
        gate.complete(Unit)
        assertEquals(self.observeAll().first().single().code, vm.opened.first())
    }

    @Test
    fun `the avatar takes its name from the player`() = runTest {
        val vm = started()
        assertEquals("Ann", vm.state.value.selfName)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
