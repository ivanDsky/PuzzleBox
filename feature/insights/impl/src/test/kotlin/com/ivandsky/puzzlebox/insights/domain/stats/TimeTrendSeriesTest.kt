package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.test
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries.Point
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTrendSeriesTest {

    private val f = StatsSeriesFixture()

    @Test
    fun `the Month range averages each Mon-Sun week, its first bucket starting on the 1st when that is not a Monday`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(1), time = 40_000),
            f.history.solve(alpha, f.day(2), time = 60_000),
            f.history.solve(alpha, f.day(4), time = 100_000),
            f.history.solve(alpha, f.day(9), time = 50_000),
        )

        val series = TimeTrendSeries.of(f.facts(StatsRange.Month), StatsRange.Month)

        assertEquals(StatsGranularity.Week, series.granularity)
        assertEquals(
            listOf(
                Point(f.day(1), averageMs = 40_000, solved = 1),
                Point(f.day(2), averageMs = 80_000, solved = 2),
                Point(f.day(9), averageMs = 50_000, solved = 1),
            ),
            series.points,
        )
        assertTrue(series.isLine)
    }

    @Test
    fun `the Week range has one point per day with a solve`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(13), time = 10_000),
            f.history.solve(alpha, f.day(14), time = 60_000),
            f.history.solve(alpha, f.day(17), time = 90_000),
        )

        val series = TimeTrendSeries.of(f.facts(StatsRange.Week), StatsRange.Week)

        assertEquals(StatsGranularity.Day, series.granularity)
        assertEquals(listOf(Point(f.day(14), 60_000, 1), Point(f.day(17), 90_000, 1)), series.points)
    }

    @Test
    fun `the All range spans months by week`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(30, month = 9), time = 30_000),
            f.history.solve(alpha, f.day(19), time = 70_000),
        )

        val series = TimeTrendSeries.of(f.facts(StatsRange.All), StatsRange.All)

        assertEquals(listOf(f.day(30, month = 9), f.day(16)), series.points.map { it.start })
    }

    @Test
    fun `one point is not a line`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(18), time = 75_000))

        val series = TimeTrendSeries.of(f.facts(StatsRange.Week), StatsRange.Week)

        assertEquals(listOf(Point(f.day(18), 75_000, 1)), series.points)
        assertFalse(series.isLine)
    }

    @Test
    fun `all equal times stay as they are, a flat line`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(3), time = 45_000),
            f.history.solve(alpha, f.day(10), time = 45_000),
            f.history.solve(alpha, f.day(17), time = 45_000),
        )

        val series = TimeTrendSeries.of(f.facts(StatsRange.Month), StatsRange.Month)

        assertEquals(listOf(45_000L, 45_000L, 45_000L), series.points.map { it.averageMs })
        assertTrue(series.isLine)
    }

    @Test
    fun `all games sum the dailies of a day into one average`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(18), time = 30_000),
            f.history.solve(beta, f.day(18), time = 90_000),
            f.history.solve(alpha, f.day(19), time = 50_000),
        )

        val all = TimeTrendSeries.of(f.facts(StatsRange.Week), StatsRange.Week)
        val onlyAlpha = TimeTrendSeries.of(f.facts(StatsRange.Week, scope = alpha), StatsRange.Week)

        assertEquals(listOf(Point(f.day(18), 60_000, 2), Point(f.day(19), 50_000, 1)), all.points)
        assertEquals(listOf(Point(f.day(18), 30_000, 1), Point(f.day(19), 50_000, 1)), onlyAlpha.points)
    }

    @Test
    fun `practice plays and unfinished days do not count`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(18), puzzleNumber = null))

        assertEquals(emptyList<Point>(), TimeTrendSeries.of(f.facts(StatsRange.Week), StatsRange.Week).points)
    }

    @Test
    fun `no solve leaves the section out`() = runTest {
        val facts = f.facts(StatsRange.Month)

        TimeTrendStatsSectionProvider.section(f.input(facts, StatsRange.Month)).test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a solve gives a ready section`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(18), time = 75_000))
        val facts = f.facts(StatsRange.Week)

        TimeTrendStatsSectionProvider.section(f.input(facts, StatsRange.Week)).test {
            val section = awaitItem() as StatsSection.TimeTrend
            assertEquals(75_000L, (section.state as SectionState.Ready).value.points.single().averageMs)
            awaitComplete()
        }
    }
}
