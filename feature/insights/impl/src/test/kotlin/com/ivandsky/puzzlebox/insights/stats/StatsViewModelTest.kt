package com.ivandsky.puzzlebox.insights.stats

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.insights.domain.stats.ObserveStatsPage
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The stats page's view model: the chip and game picked live in saved state as ids, and the first restore is waited out. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val fixture = RecapHistoryFixture()
    private val cloudSync = FakeCloudSync()

    private fun day(dayOfMonth: Int, month: Int = 11) = LocalDate.of(2026, month, dayOfMonth)

    private fun viewModel(
        initialGame: GameId? = null,
        savedState: SavedStateHandle = SavedStateHandle(),
        releaseFlags: ReleaseFlags = FakeReleaseFlags(),
    ) = StatsViewModel(
        initialGame = initialGame,
        observeStatsPage = ObserveStatsPage(
            fixture.observeHistory,
            fixture.computePeriodFacts,
            fixture.calendar,
            fixture.registry,
            rankHistory = { flowOf(null) },
            vsFriends = { flowOf(null) },
            failures = FailureLog { _, _, _ -> },
        ),
        registry = fixture.registry,
        cloudSync = cloudSync,
        savedState = savedState,
        releaseFlags = releaseFlags,
    )

    /** The next state that is no longer [StatsBody.Loading]. */
    private suspend fun app.cash.turbine.ReceiveTurbine<StatsUiState>.awaitBody(): StatsUiState {
        while (true) {
            val item = awaitItem()
            if (item.body != StatsBody.Loading) return item
        }
    }

    private fun StatsUiState.recordGames(): List<GameId> {
        val records = (body as StatsBody.Ready).sections.filterIsInstance<StatsSection.Records>().single()
        return (records.state as SectionState.Ready).value.fastest.map { it.game }
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opens on the month of every game, loading first`() = runTest {
        fixture.store(fixture.solve(alpha, day(1)), fixture.solve(beta, day(2)))

        viewModel().uiState.test {
            assertEquals(StatsUiState.loading(StatsRange.Month, null), awaitItem())
            val ready = awaitBody()
            assertEquals(StatsRange.Month, ready.range)
            assertEquals(null, ready.game)
            assertEquals(listOf(alpha, beta), ready.recordGames())
        }
    }

    @Test
    fun `the game the page was opened on scopes it`() = runTest {
        fixture.store(fixture.solve(alpha, day(1)), fixture.solve(beta, day(2)))

        viewModel(initialGame = beta).uiState.test {
            awaitItem()
            val ready = awaitBody()
            assertEquals(beta, ready.game)
            assertEquals(listOf(beta), ready.recordGames())
        }
    }

    @Test
    fun `picking a range and a game keeps ids only in saved state and rereads the page`() = runTest {
        fixture.store(fixture.solve(alpha, day(16)), fixture.solve(beta, day(17)))
        val saved = SavedStateHandle()
        val viewModel = viewModel(savedState = saved)

        viewModel.uiState.test {
            awaitItem()
            awaitBody()

            viewModel.selectRange(StatsRange.Week)
            viewModel.selectGame(alpha)
            var state = awaitBody()
            while (state.game != alpha || state.range != StatsRange.Week) state = awaitBody()

            assertEquals(listOf(alpha), state.recordGames())
            assertEquals("Week", saved.get<String>("stats.range"))
            assertEquals("alpha", saved.get<String>("stats.game"))
            assertEquals(setOf("stats.range", "stats.game"), saved.keys())
        }
    }

    @Test
    fun `saved state wins over the game the page was opened on, and null picks every game`() = runTest {
        fixture.store(fixture.solve(alpha, day(1)), fixture.solve(beta, day(2)))
        val saved = SavedStateHandle(mapOf("stats.range" to "All", "stats.game" to "alpha"))
        val viewModel = viewModel(initialGame = beta, savedState = saved)

        viewModel.uiState.test {
            awaitItem()
            val ready = awaitBody()
            assertEquals(StatsRange.All, ready.range)
            assertEquals(alpha, ready.game)

            viewModel.selectGame(null)
            var state = awaitBody()
            while (state.game != null) state = awaitBody()
            assertEquals(listOf(alpha, beta), state.recordGames())
        }
    }

    @Test
    fun `a saved game this build does not list and a saved range it does not read fall back to every game and the month`() = runTest {
        fixture.store(fixture.solve(alpha, day(1)))
        val saved = SavedStateHandle(mapOf("stats.range" to "Decade", "stats.game" to "retired"))

        viewModel(savedState = saved).uiState.test {
            val first = awaitItem()
            assertEquals(StatsRange.Month, first.range)
            assertEquals(null, first.game)
            assertEquals(null, awaitBody().game)
        }
    }

    @Test
    fun `a range with no solve is empty`() = runTest {
        fixture.store(fixture.solve(alpha, day(1, month = 10)))

        viewModel().uiState.test {
            awaitItem()
            assertEquals(StatsBody.Empty, awaitBody().body)
        }
    }

    @Test
    fun `an empty range waits out the first restore, then reads again`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)

        viewModel().uiState.test {
            awaitItem()
            assertEquals(StatsBody.Restoring, awaitBody().body)

            fixture.store(fixture.solve(alpha, day(1)))
            cloudSync.state.value = CloudSyncState.Synced(atMs = 1L)

            var state = awaitItem()
            while (state.body !is StatsBody.Ready) state = awaitItem()
            assertTrue(state.recordGames().contains(alpha))
        }
    }

    @Test
    fun `a restore that brings nothing back leaves the range empty`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)

        viewModel().uiState.test {
            awaitItem()
            assertEquals(StatsBody.Restoring, awaitBody().body)

            cloudSync.state.value = CloudSyncState.Synced(atMs = 1L)

            assertEquals(StatsBody.Empty, awaitItem().body)
        }
    }

    @Test
    fun `a range with solves does not wait for the restore`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)
        fixture.store(fixture.solve(alpha, day(1)))

        viewModel().uiState.test {
            awaitItem()
            assertTrue(awaitBody().body is StatsBody.Ready)
        }
    }

    @Test
    fun `with the stats filters flag on the records read all time, off they stay in the range`() = runTest {
        fixture.store(fixture.solve(alpha, day(1, month = 8)), fixture.solve(beta, day(18)))
        val saved = { SavedStateHandle(mapOf("stats.range" to "Week")) }

        viewModel(savedState = saved(), releaseFlags = FakeReleaseFlags(InsightsFlag.StatsFilters)).uiState.test {
            awaitItem()
            assertEquals(listOf(alpha, beta), awaitBody().recordGames())
        }
        viewModel(savedState = saved()).uiState.test {
            awaitItem()
            assertEquals(listOf(beta), awaitBody().recordGames())
        }
    }
}
