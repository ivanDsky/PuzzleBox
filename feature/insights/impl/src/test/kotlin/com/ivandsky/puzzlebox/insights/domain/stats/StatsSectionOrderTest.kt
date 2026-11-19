package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsSectionOrderTest {

    @Test
    fun `sections come in the page's order, trend then Records then hint-free then weekday then vs friends`() = runTest {
        val f = StatsSeriesFixture()
        f.history.store(f.history.solve(alpha, f.day(17)), f.history.solve(alpha, f.day(18)))
        val board = OverallState.Complete(
            OverallStandings(listOf("me", "maya").mapIndexed { index, uid -> OverallStandings.Row(uid, uid, index + 1, 100.0 - index, games = 2, truncated = false) }),
        )
        val page = ObserveStatsPage(
            observeGameHistory = f.history.observeHistory,
            computePeriodFacts = f.history.computePeriodFacts,
            calendar = f.history.calendar,
            registry = f.history.registry,
            vsFriends = f.vsFriends { _, _ -> flowOf(board) },
        )

        val sections = page(scope = null, range = StatsRange.Month).first().sections

        assertEquals(
            listOf(
                StatsSection.TimeTrend::class,
                StatsSection.Records::class,
                StatsSection.HintFreeRate::class,
                StatsSection.Weekday::class,
                StatsSection.VsFriends::class,
            ),
            sections.map { it::class },
        )
    }
}
