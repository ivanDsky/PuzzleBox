package com.ivandsky.puzzlebox.insights.hub

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecapAvailability
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.insights.FakeRecapOpenings
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The hub's list: the months with a recap, live, and why it is empty when it is. */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsHubViewModelTest {

    private val fixture = RecapHistoryFixture()
    private val cloudSync = FakeCloudSync()

    private val openings = FakeRecapOpenings()

    private fun viewModel(availability: ObserveRecapAvailability = fixture.availability()) =
        InsightsHubViewModel(availability, cloudSync, openings, fixture.puzzleDay, FailureLog { _, _, _ -> })

    /** The periods the hub lists, in order. */
    private fun InsightsHubUiState.periods() = (this as InsightsHubUiState.Ready).periods.map { it.period }

    private fun month(year: Int, month: Int) = RecapPeriod.Month(YearMonth.of(year, month))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lists the months with a solve, newest first`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 9, 1)), fixture.solve(alpha, LocalDate.of(2026, 11, 1)))

        viewModel().uiState.test {
            assertEquals(listOf(month(2026, 11), month(2026, 9)), expectMostRecentItem().periods())
        }
    }

    @Test
    fun `a solve adds its month while the hub is open`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 9, 1)))

        viewModel().uiState.test {
            assertEquals(listOf(month(2026, 9)), expectMostRecentItem().periods())

            fixture.store(fixture.solve(alpha, LocalDate.of(2026, 9, 1)), fixture.solve(alpha, LocalDate.of(2026, 11, 1)))

            assertEquals(listOf(month(2026, 11), month(2026, 9)), expectMostRecentItem().periods())
        }
    }

    @Test
    fun `nothing solved is empty`() = runTest {
        viewModel().uiState.test {
            assertEquals(InsightsHubUiState.Empty, expectMostRecentItem())
        }
    }

    @Test
    fun `nothing solved while the first restore runs says the history is coming back`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)

        viewModel().uiState.test {
            assertEquals(InsightsHubUiState.Restoring, expectMostRecentItem())

            cloudSync.state.value = CloudSyncState.Synced(atMs = 1L)

            assertEquals(InsightsHubUiState.Empty, expectMostRecentItem())
        }
    }

    @Test
    fun `months restored during the first restore replace the message`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)

        viewModel().uiState.test {
            assertEquals(InsightsHubUiState.Restoring, expectMostRecentItem())

            fixture.store(fixture.solve(alpha, LocalDate.of(2026, 10, 3)))

            assertEquals(listOf(month(2026, 10)), expectMostRecentItem().periods())
        }
    }

    @Test
    fun `year rows come before the months`() = runTest {
        fixture.store(fixture.imported(alpha, LocalDate.of(2025, 6, 1)), fixture.solve(alpha, LocalDate.of(2026, 11, 1)))

        viewModel().uiState.test {
            assertEquals(listOf(RecapPeriod.Year(2025), month(2026, 11), month(2025, 6)), expectMostRecentItem().periods())
        }
    }

    @Test
    fun `a listed month carries how many puzzles were solved in it`() = runTest {
        fixture.store(
            fixture.solve(alpha, LocalDate.of(2026, 11, 1)),
            fixture.solve(beta, LocalDate.of(2026, 11, 1)),
            fixture.solve(alpha, LocalDate.of(2026, 11, 2)),
            fixture.solve(alpha, LocalDate.of(2026, 10, 2)),
        )

        viewModel().uiState.test {
            val ready = expectMostRecentItem() as InsightsHubUiState.Ready

            assertEquals(listOf(3, 1), ready.periods.map { it.summary.solved })
        }
    }

    @Test
    fun `a period whose story was watched reads as opened, live, and the others do not`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 9, 1)), fixture.solve(alpha, LocalDate.of(2026, 11, 1)))

        viewModel().uiState.test {
            assertEquals(emptySet<String>(), (expectMostRecentItem() as InsightsHubUiState.Ready).opened)

            openings.markOpened("2026-11")

            assertEquals(setOf("2026-11"), (expectMostRecentItem() as InsightsHubUiState.Ready).opened)
        }
    }

    @Test
    fun `a failed read is an error, and trying again reads again`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 11, 1)))
        val failing = FlakyCalendar(fixture.calendar)
        val viewModel = viewModel(availability = ObserveRecapAvailability(fixture.observeHistory, failing, fixture.registry))

        viewModel.uiState.test {
            assertEquals(InsightsHubUiState.Error, expectMostRecentItem())

            failing.failing = false
            viewModel.retry()

            assertEquals(listOf(month(2026, 11)), expectMostRecentItem().periods())
        }
    }

    /** A calendar whose puzzle day cannot be read while [failing]. */
    private class FlakyCalendar(private val real: GameCalendar) : GameCalendar by real {
        var failing = true

        override fun observeToday(game: GameId): Flow<LocalDate> = if (failing) flow { throw IllegalStateException("calendar") } else real.observeToday(game)
    }
}
