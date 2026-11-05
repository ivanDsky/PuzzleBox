package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home's state. The Today card (A15) is today's set over every registered game — Zip
 * and Tango here, in registry order. The teaser observes the GAME-AGNOSTIC merged
 * history (fix round 5, defect C); Queens is not registered, so its rows exercise the
 * registry-name fallback. "Play today" targets the saved game, else the next unsolved one
 * (A13, SC11), over the core:testing fakes.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val day = LocalDate.of(2026, 11, 4)

    private data class Deps(
        val vm: HomeViewModel,
        val daily: FakeDailyRepository,
        val completions: FakeCompletionRepository,
        val saved: FakeSavedPlayRepository,
        val identity: FakeIdentityService,
    )

    private fun TestScope.viewModel(
        identity: FakeIdentityService = FakeIdentityService(initial = null),
        collect: Boolean = true,
    ): Deps {
        val registry = TestGames.registry(listOf(zip, tango))
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val saved = FakeSavedPlayRepository()
        val vm = HomeViewModel(
            observeGameHistory = ObserveGameHistory(registry, daily, completions),
            todaySet = ObserveTodaySet(registry, saved, completions, FakeGameCalendar(day)),
            identityService = identity,
            registry = registry,
        )
        if (collect) backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return Deps(vm, daily, completions, saved, identity)
    }

    private fun record(game: GameId, on: LocalDate, timeMs: Long) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, on, 916),
        time = timeMs,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    @Test
    fun `first frame lists every game with no status yet, the empty teaser and current identity`() = runTest {
        val ann = Identity("u1", "Ann", null)
        val deps = viewModel(identity = FakeIdentityService(initial = ann), collect = false)

        assertEquals(
            HomeUiState(
                today = listOf(
                    HomeUiState.TodayRow(zip, "Zip", status = null),
                    HomeUiState.TodayRow(tango, "Tango", status = null),
                ),
                identity = ann,
                history = emptyList(),
            ),
            deps.vm.uiState.value,
        )
    }

    @Test
    fun `today set shows every game's status in registry order`() = runTest {
        val deps = viewModel()
        deps.saved.save(PlaySlot(tango, day), byteArrayOf(1), elapsedMs = 42_000L)

        assertEquals(
            listOf(
                HomeUiState.TodayRow(zip, "Zip", TodayStatus.NotStarted),
                HomeUiState.TodayRow(tango, "Tango", TodayStatus.InProgress(42_000L)),
            ),
            deps.vm.uiState.value.today,
        )
        assertEquals("0 of 2 done", doneSummary(deps.vm.uiState.value.today))
    }

    @Test
    fun `a solve today flips the game to solved and the done count up`() = runTest {
        val deps = viewModel()
        assertEquals("0 of 2 done", doneSummary(deps.vm.uiState.value.today))

        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))

        assertEquals(TodayStatus.Solved(36_000L), deps.vm.uiState.value.today.first { it.game == zip }.status)
        assertEquals("1 of 2 done", doneSummary(deps.vm.uiState.value.today))
    }

    @Test
    fun `play today targets the saved game and reads resume - A13 SC11`() = runTest {
        val deps = viewModel()
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertFalse(deps.vm.uiState.value.resume)

        // Leaving Tango mid-game writes today's slot: the card offers to resume it.
        deps.saved.save(PlaySlot(tango, day), byteArrayOf(1), elapsedMs = 5_000L)
        assertEquals(tango, deps.vm.uiState.value.playTarget)
        assertTrue(deps.vm.uiState.value.resume)

        // The save goes away (finished or abandoned): back to "Play today" on the first game.
        deps.saved.delete(PlaySlot(tango, day))
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertFalse(deps.vm.uiState.value.resume)
    }

    @Test
    fun `yesterday's save or a solved today does not read resume`() = runTest {
        val deps = viewModel()

        // Yesterday's unsolved save resumes through History, not Home (A9).
        deps.saved.save(PlaySlot(zip, day.minusDays(1)), byteArrayOf(1), elapsedMs = 0L)
        assertFalse(deps.vm.uiState.value.resume)

        // A completion for today wins over a lingering save (A13: solved wins).
        deps.saved.save(PlaySlot(zip, day), byteArrayOf(1), elapsedMs = 0L)
        assertTrue(deps.vm.uiState.value.resume)
        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))
        assertFalse(deps.vm.uiState.value.resume)
    }

    @Test
    fun `play today skips solved games and falls back to the first when all are done`() = runTest {
        val deps = viewModel()

        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))
        assertEquals(tango, deps.vm.uiState.value.playTarget)

        deps.completions.onCompleted(record(tango, day, timeMs = 50_000L))
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertFalse(deps.vm.uiState.value.resume)
    }

    @Test
    fun `before the set lands play today targets the first game`() = runTest {
        val deps = viewModel(collect = false)
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertNull(deps.vm.uiState.value.today.first().status)
    }

    @Test
    fun `teaser tracks zip daily repository emissions`() = runTest {
        val deps = viewModel()
        // Nothing captured yet: the honest empty state.
        assertEquals(emptyList<HistoryDay>(), deps.vm.uiState.value.history)

        // A day lands in the compete loop's store (Play fetches/History captures) — the
        // teaser must follow, formatted like the rows the user knows.
        deps.daily.addEntry(DailyEntry(zip, LocalDate.of(2026, 10, 31), 592, "zip/n5", 0L))

        assertEquals(listOf(HistoryDay("2026-10-31", "October 31", "Zip")), deps.vm.uiState.value.history)
    }

    @Test
    fun `a queens completion surfaces in the teaser - fix round 5 defect C`() = runTest {
        val deps = viewModel()

        // A solved Queens game writes its record (no Zip capture anywhere) — the teaser
        // must surface it; the row names the game by its id (Queens is not registered,
        // so the registry fallback applies).
        deps.completions.onCompleted(record(GameId("queens"), LocalDate.of(2026, 11, 1), 41_000L))

        assertEquals(listOf(HistoryDay("2026-11-01", "November 1", "queens")), deps.vm.uiState.value.history)
    }

    @Test
    fun `identity changes reach the state`() = runTest {
        val deps = viewModel()
        val ann = Identity("u1", "Ann", null)

        deps.identity.currentIdentity = ann

        assertEquals(ann, deps.vm.uiState.value.identity)
    }
}
