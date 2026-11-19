package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.Event
import app.cash.turbine.test
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vs friends read is keyed on what it asks for: a history change that leaves the window as it was does not read again, a new range does. */
class ObserveStatsPageFriendsReadTest {

    private val f = StatsSeriesFixture()
    private val reads = mutableListOf<Pair<RankWindow, List<GameId>>>()

    private val board = OverallState.Complete(
        OverallStandings(listOf("me", "maya").mapIndexed { index, uid -> OverallStandings.Row(uid, uid, index + 1, 100.0 - index, games = 2, truncated = false) }),
    )

    private val page = ObserveStatsPage(
        observeGameHistory = f.history.observeHistory,
        computePeriodFacts = f.history.computePeriodFacts,
        calendar = f.history.calendar,
        registry = f.history.registry,
        vsFriends = f.vsFriends { window, games ->
            reads += window to games
            flowOf(board)
        },
    )

    private fun StatsPage.friends() = sections.filterIsInstance<StatsSection.VsFriends>().singleOrNull()?.state

    @Test
    fun `a new solve in the same window updates the page without reading again or loading`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(17)))

        page(scope = null, range = StatsRange.Month).test {
            var settled = awaitItem()
            while (settled.friends() !is SectionState.Ready<*>) settled = awaitItem()
            val readsBefore = reads.size
            val solvedBefore = settled.sections.filterIsInstance<StatsSection.Weekday>().single()

            f.history.store(f.history.solve(alpha, f.day(17)), f.history.solve(alpha, f.day(18)))
            runCurrent()

            val after = cancelAndConsumeRemainingEvents().filterIsInstance<Event.Item<StatsPage>>().map { it.value }
            assertTrue(after.isNotEmpty())
            assertTrue(after.all { it.friends() is SectionState.Ready<*> })
            assertTrue("the local sections update", after.last().sections.filterIsInstance<StatsSection.Weekday>().single() != solvedBefore)
            assertEquals(readsBefore, reads.size)
        }
    }

    @Test
    fun `another range reads again over its own window`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(17)))

        page(scope = null, range = StatsRange.Month).test { settle(); cancelAndIgnoreRemainingEvents() }
        val afterMonth = reads.size
        page(scope = null, range = StatsRange.Week).test { settle(); cancelAndIgnoreRemainingEvents() }

        assertTrue(afterMonth > 0)
        assertEquals(setOf<RankWindow>(RankWindow.Month(LocalDate.of(2026, 11, 1))), reads.take(afterMonth).map { it.first }.toSet())
        assertEquals(setOf<RankWindow>(RankWindow.Week(LocalDate.of(2026, 11, 16))), reads.drop(afterMonth).map { it.first }.toSet())
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<StatsPage>.settle() {
        var item = awaitItem()
        while (item.friends() !is SectionState.Ready<*>) item = awaitItem()
    }
}
