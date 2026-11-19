package com.ivandsky.puzzlebox.insights.recap

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecap
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapFactProvider
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.insights.domain.recap.RecapInput
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.domain.recap.SolvedRecapFactProvider
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The story's view model: one read per open, the first restore waited out, and a page that survives a rebuild. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelTest {

    private val fixture = RecapHistoryFixture()
    private val cloudSync = FakeCloudSync()
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))

    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 11, dayOfMonth)

    /** Offers [cards] (so a story has more than one page), or throws [failure]: a provider that needs the network and has none. */
    private class FakeRecapFactProvider(
        private val cards: List<RecapCard> = emptyList(),
        private val failure: Throwable? = null,
    ) : RecapFactProvider {
        override suspend fun cards(input: RecapInput): List<RecapCard> = failure?.let { throw it } ?: cards
    }

    private fun viewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        providers: List<RecapFactProvider> = listOf(SolvedRecapFactProvider),
    ) = RecapViewModel(
        period = november,
        observeRecap = ObserveRecap(
            fixture.observeHistory,
            fixture.computePeriodFacts,
            fixture.registry,
            providers,
            FailureLog { _, _, _ -> },
        ),
        cloudSync = cloudSync,
        savedState = savedState,
    )

    private fun solved(total: Int) = RecapCard.Solved(total, listOf(RecapCard.Solved.PerGame(alpha, total)))

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads, then shows the story`() = runTest {
        fixture.store(fixture.solve(alpha, day(1)), fixture.solve(beta, day(2)))

        viewModel().uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(
                RecapUiState.Ready(
                    period = november,
                    cards = listOf(
                        RecapCard.Solved(2, listOf(RecapCard.Solved.PerGame(alpha, 1), RecapCard.Solved.PerGame(beta, 1))),
                    ),
                    initialPage = 0,
                ),
                awaitItem(),
            )
            expectNoEvents()
        }
    }

    @Test
    fun `a period with nothing solved is empty`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 10, 31)))

        viewModel().uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(RecapUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `the cards are read once, so a solve that lands mid-story moves no page`() = runTest {
        fixture.store(fixture.solve(alpha, day(1)))

        viewModel().uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(RecapUiState.Ready::class, awaitItem()::class)

            fixture.store(fixture.solve(alpha, day(1)), fixture.solve(alpha, day(2)), fixture.solve(beta, day(3)))
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `the story opens with its card while a provider that needs the network fails`() = runTest {
        fixture.store(fixture.solve(alpha, day(1)))

        viewModel(providers = listOf(SolvedRecapFactProvider, FakeRecapFactProvider(failure = IOException("no connection")))).uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            val ready = awaitItem() as RecapUiState.Ready
            assertEquals(listOf(RecapCard.Solved(1, listOf(RecapCard.Solved.PerGame(alpha, 1)))), ready.cards)
        }
    }

    @Test
    fun `an empty period waits out the first restore, then reads again`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)

        viewModel().uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(RecapUiState.Restoring, awaitItem())

            fixture.store(fixture.solve(alpha, day(1)))
            cloudSync.state.value = CloudSyncState.Synced(atMs = 1L)

            val ready = awaitItem() as RecapUiState.Ready
            assertEquals(listOf(RecapCard.Solved(1, listOf(RecapCard.Solved.PerGame(alpha, 1)))), ready.cards)
        }
    }

    @Test
    fun `a restore that brings nothing back leaves the period empty`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)

        viewModel().uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(RecapUiState.Restoring, awaitItem())

            cloudSync.state.value = CloudSyncState.Synced(atMs = 1L)

            assertEquals(RecapUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `a sync that is not the first restore does not hold an empty period back`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = false)

        viewModel().uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(RecapUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `a story with cards does not wait for the restore`() = runTest {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)
        fixture.store(fixture.solve(alpha, day(1)))

        viewModel().uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(RecapUiState.Ready::class, awaitItem()::class)
        }
    }

    @Test
    fun `a saved page reopens the story on it`() = runTest {
        val cards = listOf(solved(1), solved(2), solved(3))
        val provider = FakeRecapFactProvider(cards)

        viewModel(SavedStateHandle(mapOf("recap.page" to 2)), listOf(provider)).uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(RecapUiState.Ready(november, cards, initialPage = 2), awaitItem())
        }
    }

    @Test
    fun `a saved page past the cards is kept on the share page after them`() = runTest {
        val cards = listOf(solved(1), solved(2))

        viewModel(SavedStateHandle(mapOf("recap.page" to 7)), listOf(FakeRecapFactProvider(cards))).uiState.test {
            assertEquals(RecapUiState.Loading, awaitItem())
            assertEquals(2, (awaitItem() as RecapUiState.Ready).initialPage)
        }
    }

    @Test
    fun `the page the story reaches is saved as a position`() {
        val saved = SavedStateHandle()

        viewModel(saved).showPage(3)

        assertEquals(3, saved.get<Int>("recap.page"))
    }
}
