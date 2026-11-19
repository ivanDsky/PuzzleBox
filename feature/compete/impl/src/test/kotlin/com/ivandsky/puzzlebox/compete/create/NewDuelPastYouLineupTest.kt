package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.compete.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.compete.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
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

/** New duel against Past you: Customize is an ordered lineup of "Any" rows over what the player has solved, and Create sends it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewDuelPastYouLineupTest {

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
    private val history = ObserveGameHistory(registry, daily, completions)
    private var gate = CompletableDeferred(Unit)
    private val pool = ComputeSolvedPool(
        registry,
        object : CompletionRepository by completions {
            override fun observeRecords(): Flow<List<CompletionRecord>> = completions.observeRecords().onStart { gate.await() }
        },
        received,
    )
    private val createSelfDuel = CreateSelfDuel(pool, completions, received, self, local, { NOW }, Random(DRAW_SEED))
    private val idTokens = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("t")
    }

    private fun TestScope.started(): NewDuelViewModel =
        NewDuelViewModel(
            createMatch = CreateMatch(server, local, ComputeMatchLegs(registry, daily, calendar, history, received, Random(DRAW_SEED)), identity, Random(CODE_SEED), ValidateExactPuzzles(FakeSharedPuzzleRepository())),
            identity = identity,
            idTokens = idTokens,
            todaySet = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, calendar, daily, SyncTodayPuzzles(registry, daily, calendar)),
            calendar = calendar,
            daily = daily,
            registry = registry,
            createSelfDuel = createSelfDuel,
            computeSolvedPool = pool,
            clock = { NOW },
            zone = { ZoneOffset.UTC },
            locale = { Locale.US },
            legTitles = noLegTitles(),
            releaseFlags = FakeReleaseFlags(),
        ).also { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} } }

    /** The player has solved [alphas] puzzles of Alpha and [betas] of Beta, each a daily of its own. */
    private fun solve(alphas: Int, betas: Int = 0) = completions.setRecords(
        listOf(alpha to alphas, beta to betas).flatMap { (game, count) ->
            (1..count).map { day ->
                CompletionRecord(
                    puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(LocalDate.of(2026, 10, day), puzzleNumber = day)),
                    time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
                )
            }
        },
    )

    private fun NewDuelViewModel.pastYou() = chooseOpponent(Opponent.PastYou)

    private fun NewDuelViewModel.rows() = state.value.lineup.draft.entries

    private fun NewDuelViewModel.add(game: GameId) {
        editLineup { it.openAdd() }
        editLineup { it.pick(AnyGame(game)) }
    }

    @Test
    fun `Customize seeds three Any rows for two games with two and one solved puzzles, and the footer reads them`() = runTest {
        solve(alphas = 2, betas = 1)
        val vm = started()
        vm.pastYou()

        vm.chooseCustom()

        assertEquals(listOf(AnyGame(alpha), AnyGame(beta), AnyGame(alpha)), vm.rows())
        assertEquals(DuelPreset.Custom, vm.state.value.self.form.preset)
        assertTrue(vm.state.value.canCreate)
        assertEquals("Best of 3 · Alpha, Beta, Alpha · against your first solves", vm.state.value.summary)
        vm.setFriends(true)
        assertEquals("Best of 3 · Alpha, Beta, Alpha · against your first solves · incl. friends' puzzles", vm.state.value.summary)
    }

    @Test
    fun `a lineup never opens with more rows of a game than the player has solved`() = runTest {
        solve(alphas = 1)
        val vm = started()
        vm.pastYou()

        vm.chooseCustom()

        assertEquals(listOf<LineupEntry>(AnyGame(alpha)), vm.rows())
    }

    @Test
    fun `with nothing solved the lineup is empty, the no-solved reason shows and Create is off`() = runTest {
        val vm = started()
        vm.pastYou()

        vm.chooseCustom()

        val state = vm.state.value
        assertTrue(vm.rows().isEmpty())
        assertTrue(state.self.empty)
        assertFalse(state.canCreate)
        vm.create()
        assertTrue(self.observeAll().first().isEmpty())
        assertFalse(vm.state.value.creating)
    }

    @Test
    fun `before the counts are read nothing is seeded, Add still works and a row turns Create on`() = runTest {
        solve(alphas = 3)
        gate = CompletableDeferred()
        val vm = started()
        vm.pastYou()
        assertNull(vm.state.value.self.counts)

        vm.chooseCustom()
        assertTrue(vm.rows().isEmpty())
        assertFalse(vm.state.value.canCreate)
        vm.add(beta)

        assertEquals(listOf<LineupEntry>(AnyGame(beta)), vm.rows())
        assertTrue(vm.state.value.canCreate)
        gate.complete(Unit)
        assertEquals(listOf<LineupEntry>(AnyGame(beta)), vm.rows())
    }

    @Test
    fun `a self duel is created from a lineup alone, with no counts read`() = runTest {
        solve(alphas = 2)
        val step = SelfDuelStep(pool, createSelfDuel)
        val form = SelfDuelForm.quick(emptyList()).copy(preset = DuelPreset.Custom, includeFriends = false)

        val outcome = step.create(form, counts = null, lineup = listOf(AnyGame(alpha), AnyGame(alpha)))

        assertEquals(2, (outcome as CreateOutcome.Created).legs.size)
        assertNull(step.create(form, counts = null, lineup = emptyList()))
        assertNull(step.create(form, counts = null))
    }

    @Test
    fun `editing the Past you lineup does not touch the friend's, and the reverse`() = runTest {
        solve(alphas = 2, betas = 1)
        val vm = started()
        val friendRows = vm.rows()
        vm.pastYou()
        vm.chooseCustom()
        val selfRows = vm.rows()

        vm.add(beta)
        assertEquals(selfRows + AnyGame(beta), vm.rows())

        vm.chooseOpponent(Opponent.Friend)
        assertEquals(friendRows, vm.rows())
        vm.editLineup { it.remove(0) }
        val editedFriend = vm.rows()
        assertEquals(friendRows.drop(1), editedFriend)

        vm.pastYou()
        assertEquals(selfRows + AnyGame(beta), vm.rows())
        vm.chooseOpponent(Opponent.Friend)
        assertEquals(editedFriend, vm.rows())
    }

    @Test
    fun `Quick and Customize keep the rows between them, and editing a row leaves Quick`() = runTest {
        solve(alphas = 2, betas = 1)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        val rows = vm.rows()

        vm.chooseQuick()
        assertEquals(DuelPreset.Quick, vm.state.value.self.form.preset)
        assertEquals(rows, vm.rows())
        vm.chooseCustom()
        assertEquals(DuelPreset.Custom, vm.state.value.self.form.preset)
        assertEquals(rows, vm.rows())

        vm.chooseQuick()
        vm.add(beta)
        assertEquals(DuelPreset.Custom, vm.state.value.self.form.preset)
        assertEquals(rows + AnyGame(beta), vm.rows())
    }

    @Test
    fun `Create sends the lineup in row order with the friends' switch`() = runTest {
        solve(alphas = 2, betas = 1)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        vm.editLineup { it.moved(0, 1) }
        vm.setFriends(true)
        assertEquals(listOf(beta, alpha, alpha), vm.rows().map { it.gameId })

        vm.create()

        val code = vm.opened.first()
        val match = self.observeAll().first().single()
        assertEquals(code, match.code)
        assertEquals(listOf(beta, alpha, alpha), match.legs.map { it.ref.gameId })
        assertTrue(match.includeFriends)
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun `an Any row with nothing left to draw keeps the lineup, marks the row and says so for Past you, and an edit clears both`() = runTest {
        solve(alphas = 2)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        vm.add(alpha)
        val rows = vm.rows()
        assertEquals(3, rows.size)

        vm.create()

        val state = vm.state.value
        assertEquals(DuelNotice.EntryUndrawable(3, "Alpha", pastYou = true), state.notice)
        assertEquals(2, state.lineup.marked)
        assertEquals(rows, vm.rows())
        assertFalse(state.creating)
        assertTrue(self.observeAll().first().isEmpty())

        vm.editLineup { it.remove(2) }

        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.lineup.marked)
        vm.create()
        vm.opened.first()
        assertEquals(2, self.observeAll().first().single().legs.size)
    }

    @Test
    fun `an exact row the player has not solved keeps the lineup and marks the row`() = runTest {
        solve(alphas = 2)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        val unknown = ExactPuzzle(LegRef.Custom(alpha, requireNotNull(ShareCode.parse("K7Q2MX"))), "bo", "Crown jewels", "Bo", PuzzleSource.Friend)
        vm.editLineup { it.openChange(1) }
        vm.editLineup { it.pick(unknown) }

        vm.create()

        assertEquals(DuelNotice.EntryUnavailable(2, pastYou = true), vm.state.value.notice)
        assertEquals(1, vm.state.value.lineup.marked)
        assertTrue(self.observeAll().first().isEmpty())
    }

    @Test
    fun `Quick draws by the count, whatever rows Customize holds`() = runTest {
        solve(alphas = 3)
        val vm = started()
        vm.pastYou()
        vm.chooseCustom()
        vm.editLineup { it.remove(0) }
        assertEquals(2, vm.rows().size)
        vm.chooseQuick()

        vm.create()

        vm.opened.first()
        assertEquals(3, self.observeAll().first().single().legs.size)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
