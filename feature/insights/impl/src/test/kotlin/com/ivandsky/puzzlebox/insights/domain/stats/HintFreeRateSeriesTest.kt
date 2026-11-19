package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.test
import com.ivandsky.puzzlebox.insights.domain.stats.HintFreeRateSeries.Point
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintFreeRateSeriesTest {

    private val f = StatsSeriesFixture()

    @Test
    fun `the Month range is the hint-free share of each week`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(2)),
            f.history.solve(alpha, f.day(3), hints = 1),
            f.history.solve(alpha, f.day(4), hints = 2),
            f.history.solve(alpha, f.day(5)),
            f.history.solve(alpha, f.day(10)),
        )

        val series = HintFreeRateSeries.of(f.facts(StatsRange.Month), StatsRange.Month)

        assertEquals(StatsGranularity.Week, series.granularity)
        assertEquals(listOf(Point(f.day(2), hintFree = 2, solved = 4), Point(f.day(9), hintFree = 1, solved = 1)), series.points)
        assertEquals(listOf(0.5, 1.0), series.points.map { it.share })
        assertTrue(series.isLine)
    }

    @Test
    fun `the first bucket of a Month range starts on the 1st, not the Monday before it`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(1)), f.history.solve(alpha, f.day(3)))

        val series = HintFreeRateSeries.of(f.facts(StatsRange.Month), StatsRange.Month)

        assertEquals(listOf(f.day(1), f.day(2)), series.points.map { it.start })
    }

    @Test
    fun `the Week range has one point per day`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(18)),
            f.history.solve(beta, f.day(18), hints = 1),
            f.history.solve(alpha, f.day(19), hints = 1),
        )

        val series = HintFreeRateSeries.of(f.facts(StatsRange.Week), StatsRange.Week)

        assertEquals(StatsGranularity.Day, series.granularity)
        assertEquals(listOf(0.5, 0.0), series.points.map { it.share })
    }

    @Test
    fun `an import counts by its hint flag like any solve`() = runTest {
        f.history.store(f.history.imported(alpha, f.day(18)), f.history.imported(beta, f.day(18)).copy(hints = 1))

        val series = HintFreeRateSeries.of(f.facts(StatsRange.Week), StatsRange.Week)

        assertEquals(listOf(Point(f.day(18), hintFree = 1, solved = 2)), series.points)
    }

    @Test
    fun `all games sum the dailies, one game reads its own`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(18)),
            f.history.solve(beta, f.day(18), hints = 1),
        )

        val all = HintFreeRateSeries.of(f.facts(StatsRange.Week), StatsRange.Week)
        val onlyBeta = HintFreeRateSeries.of(f.facts(StatsRange.Week, scope = beta), StatsRange.Week)

        assertEquals(listOf(0.5), all.points.map { it.share })
        assertEquals(listOf(0.0), onlyBeta.points.map { it.share })
    }

    @Test
    fun `one point is not a line`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(18)))

        assertFalse(HintFreeRateSeries.of(f.facts(StatsRange.Week), StatsRange.Week).isLine)
    }

    @Test
    fun `all equal shares stay as they are`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(3)), f.history.solve(alpha, f.day(10)), f.history.solve(alpha, f.day(17)))

        val series = HintFreeRateSeries.of(f.facts(StatsRange.Month), StatsRange.Month)

        assertEquals(listOf(1.0, 1.0, 1.0), series.points.map { it.share })
    }

    @Test
    fun `no solve leaves the section out`() = runTest {
        val facts = f.facts(StatsRange.Month)

        HintFreeStatsSectionProvider.section(f.input(facts, StatsRange.Month)).test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a solve gives a ready section`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(18)))
        val facts = f.facts(StatsRange.Week)

        HintFreeStatsSectionProvider.section(f.input(facts, StatsRange.Week)).test {
            val section = awaitItem() as StatsSection.HintFreeRate
            assertEquals(1.0, (section.state as SectionState.Ready).value.points.single().share, 0.0)
            awaitComplete()
        }
    }
}
