package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.domain.GameSummary
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObservePlayProgress
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home's state (fix round 5, defect C): the teaser observes the GAME-AGNOSTIC merged
 * history — every game's completion records UNION every registered game's captured
 * days. Only Zip is registered, so Queens rows exercise the registry-name fallback. The
 * resume cue follows today's featured save (A13, SC11) over the core:testing fakes.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zip = GameId("zip")
    private val today = LocalDate.of(2026, 11, 4)

    private data class Deps(
        val vm: HomeViewModel,
        val daily: FakeDailyRepository,
        val completions: FakeCompletionRepository,
        val identity: FakeIdentityService,
        val saved: FakeSavedPlayRepository,
    )

    private fun TestScope.viewModel(identity: FakeIdentityService = FakeIdentityService(initial = null)): Deps {
        val registry = TestGames.registry(listOf(zip))
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val saved = FakeSavedPlayRepository()
        val vm = HomeViewModel(
            observeGameHistory = ObserveGameHistory(registry, daily, completions),
            getFeaturedGame = GetFeaturedGame(registry),
            observePlayProgress = ObservePlayProgress(registry, saved, completions, FakeGameCalendar(today)),
            identityService = identity,
            registry = registry,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return Deps(vm, daily, completions, identity, saved)
    }

    private fun zipRecord(day: LocalDate) = CompletionRecord(
        puzzleRef = TestGames.Ref(zip, day, 592),
        time = 30_000L,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    @Test
    fun `first frame is the empty teaser, featured game and current identity`() = runTest {
        val ann = Identity("u1", "Ann", null)
        val deps = viewModel(identity = FakeIdentityService(initial = ann))

        assertEquals(
            HomeUiState(GameSummary(zip, "Zip"), ann, emptyList(), resumeFeatured = false),
            deps.vm.uiState.value,
        )
    }

    @Test
    fun `resume flag follows today's featured save - A13 SC11`() = runTest {
        val deps = viewModel()
        assertFalse(deps.vm.uiState.value.resumeFeatured)

        // Leaving Zip mid-game writes today's slot: the card must offer to resume it.
        deps.saved.save(PlaySlot(zip, today), byteArrayOf(1))
        assertTrue(deps.vm.uiState.value.resumeFeatured)

        // The save goes away (finished or abandoned): back to "Play today".
        deps.saved.delete(PlaySlot(zip, today))
        assertFalse(deps.vm.uiState.value.resumeFeatured)
    }

    @Test
    fun `yesterday's save or a solved today does not read resume`() = runTest {
        val deps = viewModel()

        // Yesterday's unsolved save resumes through History, not Home (A9).
        deps.saved.save(PlaySlot(zip, today.minusDays(1)), byteArrayOf(1))
        assertFalse(deps.vm.uiState.value.resumeFeatured)

        // A completion for today wins over a lingering save (A13: solved wins).
        deps.saved.save(PlaySlot(zip, today), byteArrayOf(1))
        assertTrue(deps.vm.uiState.value.resumeFeatured)
        deps.completions.onCompleted(zipRecord(today))
        assertFalse(deps.vm.uiState.value.resumeFeatured)
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
        deps.completions.onCompleted(
            CompletionRecord(
                puzzleRef = TestGames.Ref(GameId("queens"), LocalDate.of(2026, 11, 1), 916),
                time = 41_000L,
                hints = 1,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
            ),
        )

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
