package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.domain.GameSummary
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Home's state (fix round 5, defect C): the teaser observes the GAME-AGNOSTIC merged
 * history — every game's completion records UNION every registered game's captured
 * days. Only Zip is registered, so Queens rows exercise the registry-name fallback.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zip = GameId("zip")

    private data class Deps(
        val vm: HomeViewModel,
        val daily: FakeDailyRepository,
        val completions: FakeCompletionRepository,
        val identity: FakeIdentityService,
    )

    private fun TestScope.viewModel(identity: FakeIdentityService = FakeIdentityService(initial = null)): Deps {
        val registry = TestGames.registry(listOf(zip))
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val vm = HomeViewModel(
            observeGameHistory = ObserveGameHistory(registry, daily, completions),
            getFeaturedGame = GetFeaturedGame(registry),
            identityService = identity,
            registry = registry,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return Deps(vm, daily, completions, identity)
    }

    @Test
    fun `first frame is the empty teaser, featured game and current identity`() = runTest {
        val ann = Identity("u1", "Ann", null)
        val deps = viewModel(identity = FakeIdentityService(initial = ann))

        assertEquals(HomeUiState(GameSummary(zip, "Zip"), ann, emptyList()), deps.vm.uiState.value)
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
