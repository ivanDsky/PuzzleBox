package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
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

/** New duel with the DuelLineup flag: Customize edits an ordered lineup and Create sends it; with the flag off nothing changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewDuelLineupTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
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
    private val received = FakeReceivedPuzzleRepository()
    private val identity = FakeIdentityService(ANN)
    private val server = FakeMatchRepository().apply { signedInUid = ANN.uid }
    private val repo = RecordingMatchRepository(server)
    private val local = FakeMatchLocalRepository()
    private val history = ObserveGameHistory(registry, daily, completions)
    private val createMatch = CreateMatch(
        repo, local, ComputeMatchLegs(registry, daily, calendar, history, received, Random(DRAW_SEED)), identity, Random(CODE_SEED),
    )
    private val idTokens = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("t")
    }

    /** The fake server behind a recorder; [offlineOnce] makes the first write answer Offline, as a dropped connection does. */
    private class RecordingMatchRepository(private val server: FakeMatchRepository) : MatchRepository by server {
        val drafts = mutableListOf<MatchDraft>()
        var offlineOnce = false

        override suspend fun create(draft: MatchDraft): CreateResult {
            drafts += draft
            if (offlineOnce) {
                offlineOnce = false
                return CreateResult.Offline
            }
            return server.create(draft)
        }
    }

    private fun TestScope.started(flagOn: Boolean = true): NewDuelViewModel =
        NewDuelViewModel(
            createMatch = createMatch,
            identity = identity,
            idTokens = idTokens,
            todaySet = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, calendar, daily, SyncTodayPuzzles(registry, daily, calendar)),
            calendar = calendar,
            daily = daily,
            registry = registry,
            createSelfDuel = CreateSelfDuel(ComputeSolvedPool(registry, completions, received), completions, received, FakeSelfMatchRepository(), local, { NOW }, Random(DRAW_SEED)),
            computeSolvedPool = ComputeSolvedPool(registry, completions, received),
            clock = { NOW },
            zone = { ZoneOffset.UTC },
            locale = { Locale.US },
            releaseFlags = FakeReleaseFlags(*if (flagOn) arrayOf(ReleaseFlag.DuelLineup) else emptyArray()),
        ).also { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} } }

    private fun NewDuelViewModel.lineupGames(): List<GameId> = state.value.lineup!!.entries.map { it.gameId }

    private fun NewDuelViewModel.pick(game: GameId, row: Int? = null) {
        editLineup { if (row == null) it.openAdd() else it.openChange(row) }
        editLineup { it.pick(AnyGame(game)) }
    }

    @Test
    fun `adding, changing and removing rows in the sheet creates the duel with exactly those puzzles in order`() = runTest {
        val vm = started()
        assertEquals(3, vm.state.value.lineup!!.draft.size)
        vm.pick(alpha)
        vm.pick(beta)
        vm.pick(beta)
        repeat(3) { vm.editLineup { it.remove(0) } }
        assertEquals(listOf(alpha, beta, beta), vm.lineupGames())
        vm.pick(alpha, row = 1)
        vm.editLineup { it.remove(0) }
        assertEquals(listOf(alpha, beta), vm.lineupGames())

        vm.create()

        val (code, puzzles) = vm.created.first()
        val legs = server.matches.getValue(code).legs
        assertEquals(listOf(alpha, beta), legs.map { it.ref.gameId })
        assertEquals(listOf("Alpha", "Beta"), puzzles)
    }

    @Test
    fun `the footer reads the lineup in order once Customize is chosen`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.pick(beta)

        assertEquals("Best of 4 · Alpha, Beta, Gamma, Beta · ends Tue 10:00", vm.state.value.summary)
        assertEquals(DuelPreset.Custom, vm.state.value.draft.preset)
    }

    @Test
    fun `with the flag off the state has no lineup and Customize is today's draft`() = runTest {
        val vm = started(flagOn = false)
        vm.setCount(5)

        assertNull(vm.state.value.lineup)
        assertEquals(DuelDraft.quick(vm.state.value.games).withCount(5), vm.state.value.draft)
        vm.create()
        val (code, puzzles) = vm.created.first()
        assertEquals(5, puzzles.size)
        assertEquals(5, server.matches.getValue(code).legs.size)
    }

    private fun today(game: GameId) = ExactPuzzle(
        LegRef.Daily(game, today, (today.toEpochDay() % 10_000).toInt() + 1), null, "${if (game == alpha) "Alpha" else "Beta"} #${(today.toEpochDay() % 10_000).toInt() + 1}", null, PuzzleSource.PastDay,
    )

    @Test
    fun `Quick is untouched by the lineup, and its rows stay for Customize`() = runTest {
        val vm = started()
        val rows = vm.state.value.lineup!!.draft

        vm.chooseQuick()
        assertEquals(DuelPreset.Quick, vm.state.value.draft.preset)
        vm.pick(beta)
        vm.chooseCustom()

        assertEquals(DuelPreset.Custom, vm.state.value.draft.preset)
        assertEquals(rows.entries + AnyGame(beta), vm.state.value.lineup!!.draft.entries)
    }

    @Test
    fun `Daily shows today's unsolved puzzles as rows, removes down to one, and Customize keeps them`() = runTest {
        val vm = started()

        vm.chooseDaily()

        assertEquals(DuelPreset.Daily, vm.state.value.draft.preset)
        assertEquals(listOf(today(alpha), today(beta)), vm.state.value.lineup!!.draft.entries)
        assertTrue(vm.state.value.draft.showsRows(vm.state.value.lineup, vm.state.value.games))

        vm.editLineup { it.remove(0) }
        assertEquals(listOf(today(beta)), vm.state.value.lineup!!.draft.entries)
        vm.editLineup { it.remove(0) }
        assertEquals(listOf(today(beta)), vm.state.value.lineup!!.draft.entries)
        assertEquals(DuelPreset.Daily, vm.state.value.draft.preset)

        vm.chooseDaily()
        vm.chooseCustom()
        assertEquals(DuelPreset.Custom, vm.state.value.draft.preset)
        assertEquals(listOf(today(alpha), today(beta)), vm.state.value.lineup!!.draft.entries)
        vm.chooseDaily()
        assertEquals(DuelPreset.Daily, vm.state.value.draft.preset)
        assertEquals(listOf(today(alpha), today(beta)), vm.state.value.lineup!!.draft.entries)
    }

    @Test
    fun `a Daily puzzle of an earlier day is not today's, so Daily replaces its rows instead of keeping them`() = runTest {
        val vm = started()
        val old = today(alpha).copy(ref = LegRef.Daily(alpha, today.minusDays(1), 5), title = "Alpha #5")
        vm.chooseCustom()
        vm.editLineup { it.openChange(0) }
        vm.editLineup { it.pick(old) }
        vm.editLineup { it.remove(2) }
        vm.editLineup { it.remove(1) }
        assertEquals(listOf<LineupEntry>(old), vm.state.value.lineup!!.draft.entries)

        vm.chooseDaily()

        assertEquals(listOf(today(alpha), today(beta)), vm.state.value.lineup!!.draft.entries)
        assertTrue(vm.state.value.draft.showsRows(vm.state.value.lineup, vm.state.value.games))
    }

    @Test
    fun `a past day picked into the Daily rows is not today's, so the rows stop being the Daily duel's`() = runTest {
        val vm = started()
        vm.chooseDaily()
        val past = today(alpha).copy(ref = LegRef.Daily(alpha, today.minusDays(3), 5), title = "Alpha #5")

        vm.editLineup { it.openAdd() }
        vm.editLineup { it.pick(past) }

        assertEquals(listOf(today(alpha), today(beta), past), vm.state.value.lineup!!.draft.entries)
        assertFalse(vm.state.value.lineup!!.draft.isToday(vm.state.value.games))
        assertFalse(vm.state.value.draft.showsRows(vm.state.value.lineup, vm.state.value.games))
    }

    @Test
    fun `Daily from Customize rows that are not today's replaces them with today's`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.pick(beta)

        vm.chooseDaily()

        assertEquals(listOf(today(alpha), today(beta)), vm.state.value.lineup!!.draft.entries)
    }

    @Test
    fun `a reordered Daily creates the duel with today's puzzles in the rows' order`() = runTest {
        val vm = started()
        vm.chooseDaily()
        vm.editLineup { it.moved(0, 1) }

        vm.create()

        val (code, _) = vm.created.first()
        assertEquals(listOf(today(beta).ref, today(alpha).ref), server.matches.getValue(code).legs.map { it.ref })
        assertEquals(DuelPreset.Daily, vm.state.value.draft.preset)
    }

    @Test
    fun `with the flag off Daily has no rows and is today's draft`() = runTest {
        val vm = started(flagOn = false)

        vm.chooseDaily()

        assertNull(vm.state.value.lineup)
        assertEquals(DuelDraft.daily(vm.state.value.games), vm.state.value.draft)
    }

    @Test
    fun `Quick still draws three mixed puzzles with the flag on`() = runTest {
        val vm = started()

        vm.create()

        val (code, puzzles) = vm.created.first()
        assertEquals(3, puzzles.size)
        assertEquals(3, server.matches.getValue(code).legs.size)
    }

    @Test
    fun `a retry after Offline sends the same legs, not a new draw`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.editLineup { it.remove(2) }
        repeat(8) { vm.pick(alpha) }
        assertEquals(10, vm.state.value.lineup!!.draft.size)
        repo.offlineOnce = true

        vm.create()
        assertEquals(DuelNotice.Offline, vm.state.value.notice)
        vm.create()

        val (code, _) = vm.created.first()
        assertEquals(2, repo.drafts.size)
        assertEquals(repo.drafts[0].legs, repo.drafts[1].legs)
        assertEquals(repo.drafts[0].legs, server.matches.getValue(code).legs)
        assertEquals(10, repo.drafts[1].legs.map { it.ref }.toSet().size)
    }

    @Test
    fun `an edit after a failure drops the pinned legs so the next try draws again`() = runTest {
        val vm = started()
        vm.chooseCustom()
        repo.offlineOnce = true
        vm.create()
        val before = vm.state.value.lineup!!.entries

        vm.pick(beta)

        assertEquals(vm.state.value.lineup!!.draft.entries, vm.state.value.lineup!!.entries)
        assertTrue(before != vm.state.value.lineup!!.entries)
    }

    @Test
    fun `an Any row with nothing to draw says which puzzle and game, marks the row, and clears once the row is changed`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.editLineup { it.remove(2) }
        vm.pick(gamma, row = 1)

        vm.create()

        assertEquals(DuelNotice.EntryUndrawable(2, "Gamma"), vm.state.value.notice)
        assertEquals(1, vm.state.value.lineup!!.marked)
        assertTrue(server.matches.isEmpty())

        vm.pick(alpha, row = 1)

        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.lineup!!.marked)
        vm.create()
        assertEquals(2, server.matches.getValue(vm.created.first().code).legs.size)
    }

    @Test
    fun `moving a row reorders the footer and what Create sends, and nothing else about the rows`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.editLineup { it.remove(2) }
        vm.pick(beta)

        vm.editLineup { it.moved(2, 0) }

        assertEquals(listOf(beta, alpha, beta), vm.lineupGames())
        assertEquals("Best of 3 · Beta, Alpha, Beta · ends Tue 10:00", vm.state.value.summary)
        vm.create()
        assertEquals(listOf(beta, alpha, beta), server.matches.getValue(vm.created.first().code).legs.map { it.ref.gameId })
    }

    @Test
    fun `after a failed try the footer still names the games, not the drawn puzzles' ids`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.editLineup { it.remove(2) }
        repo.offlineOnce = true

        vm.create()

        assertEquals(DuelNotice.Offline, vm.state.value.notice)
        assertEquals("Best of 2 · Alpha, Beta · ends Tue 10:00", vm.state.value.summary)
    }

    @Test
    fun `the marked row is cleared with its notice when the next try starts`() = runTest {
        val vm = started()
        vm.chooseCustom()
        vm.pick(gamma, row = 1)
        vm.create()
        assertEquals(1, vm.state.value.lineup!!.marked)

        vm.setDeadline(48)

        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.lineup!!.marked)
    }

    @Test
    fun `opening a row or the add sheet, then dismissing it, clears a refusal with its notice`() = runTest {
        val edits = listOf<List<(LineupEditor) -> LineupEditor>>(
            listOf({ it.openChange(0) }),
            listOf({ it.openAdd() }),
            listOf({ it.openChange(0) }, { it.dismiss() }),
        )
        for (sequence in edits) {
            val vm = started()
            vm.chooseCustom()
            vm.pick(gamma, row = 1)
            vm.create()
            assertEquals(1, vm.state.value.lineup!!.marked)

            sequence.forEach { vm.editLineup(it) }

            assertNull(vm.state.value.notice)
            assertNull(vm.state.value.lineup!!.marked)
        }
    }

    @Test
    fun `a challenge opens with one Any row of its game`() = runTest {
        val vm = started()

        vm.prefill(DuelChallenge(friendUid = "sam", friendName = "Sam", game = beta))

        assertEquals(listOf(beta), vm.lineupGames())
    }

    @Test
    fun `closing the sheet gives the next opening the three opening rows again`() = runTest {
        val vm = started()
        vm.pick(beta)
        vm.editLineup { it.openChange(0) }

        vm.reset()

        val editor: LineupEditor = vm.state.value.lineup!!
        assertEquals(3, editor.draft.size)
        assertNull(editor.target)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
