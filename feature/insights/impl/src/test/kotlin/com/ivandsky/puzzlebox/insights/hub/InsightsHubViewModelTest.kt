package com.ivandsky.puzzlebox.insights.hub

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
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

    private fun viewModel() = InsightsHubViewModel(fixture.availability(), cloudSync)

    private fun month(year: Int, month: Int) = RecapPeriod.Month(YearMonth.of(year, month))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lists the months with a solve, newest first`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 9, 1)), fixture.solve(alpha, LocalDate.of(2026, 11, 1)))

        viewModel().uiState.test {
            assertEquals(InsightsHubUiState.Ready(listOf(month(2026, 11), month(2026, 9))), expectMostRecentItem())
        }
    }

    @Test
    fun `a solve adds its month while the hub is open`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 9, 1)))

        viewModel().uiState.test {
            assertEquals(InsightsHubUiState.Ready(listOf(month(2026, 9))), expectMostRecentItem())

            fixture.store(fixture.solve(alpha, LocalDate.of(2026, 9, 1)), fixture.solve(alpha, LocalDate.of(2026, 11, 1)))

            assertEquals(InsightsHubUiState.Ready(listOf(month(2026, 11), month(2026, 9))), expectMostRecentItem())
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

            assertEquals(InsightsHubUiState.Ready(listOf(month(2026, 10))), expectMostRecentItem())
        }
    }
}
