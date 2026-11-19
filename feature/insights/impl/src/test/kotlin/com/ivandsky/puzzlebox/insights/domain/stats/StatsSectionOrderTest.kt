package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsSectionOrderTest {

    @Test
    fun `sections come in the page's order, trend first, Records second, hint-free third`() = runTest {
        val f = StatsSeriesFixture()
        f.history.store(f.history.solve(alpha, f.day(17)), f.history.solve(alpha, f.day(18)))
        val page = ObserveStatsPage(
            observeGameHistory = f.history.observeHistory,
            computePeriodFacts = f.history.computePeriodFacts,
            calendar = f.history.calendar,
            registry = f.history.registry,
        )

        val sections = page(scope = null, range = StatsRange.Month).first().sections

        assertEquals(
            listOf(StatsSection.TimeTrend::class, StatsSection.Records::class, StatsSection.HintFreeRate::class),
            sections.map { it::class },
        )
    }
}
