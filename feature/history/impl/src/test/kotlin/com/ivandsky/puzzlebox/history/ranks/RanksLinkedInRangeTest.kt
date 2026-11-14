package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * What Friends scope listens to of the phone's LinkedIn days: one range a game for the window's days, never one
 * listener a day, so a month costs as many listeners as a day does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RanksLinkedInRangeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val games = listOf(ZIP, TANGO, QUEENS, GameId("strands"), GameId("minisudoku"))
    private val hub = RanksTestHub(FakeLeaderboardRepository(), Identity("u1", "Ann", null), TestGames.registry(games), today)

    private fun TestScope.friends(window: RanksSelection.Window): RanksViewModel {
        val viewModel = hub.viewModel(linkedIn = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        viewModel.select { it.setScope(RankScope.Friends).setWindow(window) }
        runCurrent()
        return viewModel
    }

    @Test
    fun `Friends Month listens to one range a game, the month so far`() = runTest {
        friends(RanksSelection.Window.Month)

        assertEquals(games.map { Triple(it, today.withDayOfMonth(1), today) }, hub.linkedInDays.observedRanges)
        assertEquals(emptyList<Pair<GameId, LocalDate>>(), hub.linkedInDays.observedDays)
    }

    @Test
    fun `Friends Day listens to one range a game, of its one day`() = runTest {
        friends(RanksSelection.Window.Day)

        assertEquals(games.map { Triple(it, today, today) }, hub.linkedInDays.observedRanges)
        assertEquals(emptyList<Pair<GameId, LocalDate>>(), hub.linkedInDays.observedDays)
    }

    @Test
    fun `Global listens to no LinkedIn day`() = runTest {
        val viewModel = hub.viewModel(linkedIn = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        viewModel.select { it.setWindow(RanksSelection.Window.Month) }
        runCurrent()

        assertEquals(emptyList<Triple<GameId, LocalDate, LocalDate>>(), hub.linkedInDays.observedRanges)
        assertEquals(emptyList<Pair<GameId, LocalDate>>(), hub.linkedInDays.observedDays)
    }
}
