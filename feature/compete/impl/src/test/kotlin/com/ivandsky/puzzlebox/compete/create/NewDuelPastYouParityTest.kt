package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.compete.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.compete.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.domain.lineup.ValidateExactPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
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
import kotlinx.coroutines.flow.first
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

/** Past you's lineup leaves the rest of New duel as it was, and holds at the edges: 1 and 10 puzzles, a puzzle taken down, a live solve, a close. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewDuelPastYouParityTest {

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
    private var self = FakeSelfMatchRepository()
    private val history = ObserveGameHistory(registry, daily, completions)
    private val pool = ComputeSolvedPool(registry, completions, received)
    private val idTokens = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("t")
    }

    /** A new view model over a fresh self store and a fresh draw, so two runs of it can be compared. */
    private fun TestScope.started(): NewDuelViewModel {
        self = FakeSelfMatchRepository()
        return NewDuelViewModel(
            createMatch = CreateMatch(server, local, ComputeMatchLegs(registry, daily, calendar, history, received, Random(DRAW_SEED)), identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())),
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
            releaseFlags = FakeReleaseFlags(),
        ).also { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} } }
    }

    private fun record(game: GameId, key: PuzzleKey) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, key), time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
    )

    private fun dailies(game: GameId, count: Int) = (1..count).map { record(game, PuzzleKey.Daily(LocalDate.of(2026, 10, it), puzzleNumber = it)) }

    /** The player has solved [alphas] puzzles of Alpha and [betas] of Beta, each a daily of its own. */
    private fun solve(alphas: Int, betas: Int = 0) = completions.setRecords(dailies(alpha, alphas) + dailies(beta, betas))

    private fun NewDuelViewModel.pastYou() = chooseOpponent(Opponent.PastYou)

    private fun NewDuelViewModel.rows() = state.value.lineup.draft.entries

    private fun NewDuelViewModel.add(game: GameId) {
        editLineup { it.openAdd() }
        editLineup { it.pick(AnyGame(game)) }
    }

    private suspend fun NewDuelViewModel.created(): Match {
        val code = opened.first()
        return self.observeAll().first().single { it.code == code }
    }

    @Test
    fun `Quick draws the same puzzles whatever rows Customize holds`() = runTest {
        solve(alphas = 3, betas = 3)
        val drawn = listOf(true, false).map { edited ->
            val vm = started()
            vm.pastYou()
            if (edited) {
                vm.chooseCustom()
                vm.editLineup { it.moved(0, 2) }
                vm.chooseQuick()
            }
            vm.create()
            vm.created().legs
        }

        assertEquals(3, drawn.first().size)
        assertEquals(drawn.first(), drawn.last())
    }

    @Test
    fun `each opponent keeps its own preset and rows while the other is shown`() = runTest {
        solve(alphas = 2, betas = 1)
        val vm = started()
        vm.chooseDaily()
        vm.setDeadline(Match.DEADLINE_CHOICES.last())
        val friendDraft = vm.state.value.draft
        val friendRows = vm.rows()
        vm.pastYou()
        vm.chooseCustom()
        vm.add(beta)
        val selfForm = vm.state.value.self.form
        val selfRows = vm.rows()

        vm.chooseOpponent(Opponent.Friend)
        assertEquals(DuelPreset.Daily, vm.state.value.draft.preset)
        assertEquals(friendDraft, vm.state.value.draft)
        assertEquals(friendRows, vm.rows())
        vm.pastYou()

        assertEquals(selfForm, vm.state.value.self.form)
        assertEquals(selfRows, vm.rows())
    }

    @Test
    fun `a refusal's notice and marked row stay on the side that was refused`() = runTest {
        solve(alphas = 2)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        vm.add(alpha)
        vm.create()
        assertEquals(2, vm.state.value.lineup.marked)

        vm.chooseOpponent(Opponent.Friend)
        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.lineup.marked)
        server.online = false
        vm.create()
        assertEquals(DuelNotice.Offline, vm.state.value.notice)

        vm.pastYou()
        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.lineup.marked)
        assertEquals(3, vm.rows().size)
    }

    @Test
    fun `one solved puzzle is a lineup of one that keeps its only row, and Create sends it`() = runTest {
        solve(alphas = 1)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()

        vm.editLineup { it.remove(0) }

        assertEquals(listOf<LineupEntry>(AnyGame(alpha)), vm.rows())
        assertTrue(vm.state.value.canCreate)
        vm.create()
        assertEquals(1, vm.created().legs.size)
    }

    @Test
    fun `ten rows are the most, Add does nothing there, and Create stores ten different puzzles`() = runTest {
        solve(alphas = 12)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        repeat(Match.MAX_LEGS) { vm.add(alpha) }

        vm.editLineup { it.openAdd() }

        assertEquals(Match.MAX_LEGS, vm.rows().size)
        assertNull(vm.state.value.lineup.target)
        vm.create()
        assertEquals(Match.MAX_LEGS, vm.created().legs.map { it.ref }.toSet().size)
    }

    @Test
    fun `a friend's puzzle taken down between the pick and Create marks its row, stores nothing, and Create works once it goes`() = runTest {
        val code = ShareCode.parse("K7Q2MX")!!
        received.upsert(
            ReceivedPuzzle(
                code = code, gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1, puzzleBytes = byteArrayOf(TestGames.MAGIC),
                title = "Crown jewels", authorName = "Bo", note = "", authorTimeMs = 1_000, authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH,
                unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
            ),
        )
        completions.setRecords(dailies(alpha, 2) + record(alpha, PuzzleKey.Custom(code.value)))
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        vm.editLineup { it.openChange(1) }
        vm.editLineup { it.pick(ExactPuzzle(LegRef.Custom(alpha, code), "bo", "Crown jewels", "Bo", PuzzleSource.Friend)) }
        received.markUnpublished(code)

        vm.create()

        assertEquals(DuelNotice.EntryUnavailable(2, pastYou = true), vm.state.value.notice)
        assertEquals(1, vm.state.value.lineup.marked)
        assertTrue(self.observeAll().first().isEmpty())
        vm.editLineup { it.remove(1) }
        vm.create()
        assertEquals(1, vm.created().legs.size)
    }

    @Test
    fun `a puzzle solved while the sheet is open is counted at once, with no reopening`() = runTest {
        solve(alphas = 1)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        assertEquals(1, vm.state.value.self.counts?.of(alpha))

        solve(alphas = 2, betas = 1)

        assertEquals(2, vm.state.value.self.counts?.of(alpha))
        assertEquals(1, vm.state.value.self.counts?.of(beta))
    }

    @Test
    fun `closing the sheet throws Past you's rows away, and the next Customize opens fresh`() = runTest {
        solve(alphas = 2, betas = 1)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        val opened = vm.rows()
        vm.add(beta)
        vm.editLineup { it.moved(0, 3) }
        assertEquals(4, vm.rows().size)

        vm.reset()
        vm.pastYou()
        vm.chooseCustom()

        assertEquals(opened, vm.rows())
        assertFalse(vm.state.value.lineup.changing)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
