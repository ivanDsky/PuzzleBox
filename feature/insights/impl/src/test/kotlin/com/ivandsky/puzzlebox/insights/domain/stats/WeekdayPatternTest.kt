package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.test
import com.ivandsky.puzzlebox.insights.domain.stats.WeekdayPattern.Day
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekdayPatternTest {

    private val f = StatsSeriesFixture()

    @Test
    fun `a weekday is the average of its solves, Monday first`() = runTest {
        // Nov 2026: the 2nd is a Monday, the 4th a Wednesday, the 11th the next Wednesday.
        f.history.store(
            f.history.solve(alpha, f.day(11), time = 90_000),
            f.history.solve(alpha, f.day(4), time = 60_000),
            f.history.solve(alpha, f.day(2), time = 100_000),
        )

        val pattern = WeekdayPattern.of(f.facts(StatsRange.Month))

        assertEquals(listOf(Day(DayOfWeek.MONDAY, 100_000, 1), Day(DayOfWeek.WEDNESDAY, 75_000, 2)), pattern.days)
    }

    @Test
    fun `a weekday with no solves is hidden`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(3)), f.history.solve(alpha, f.day(7)))

        val pattern = WeekdayPattern.of(f.facts(StatsRange.Month))

        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), pattern.days.map { it.weekday })
    }

    @Test
    fun `the weekday is the puzzle day's, not the instant an import was made`() = runTest {
        // The puzzle day Nov 18 is a Wednesday; the import was made on Thursday Nov 5 (and Sunday Nov 22 for the other).
        f.history.store(
            f.history.imported(alpha, f.day(18), importedAt = Instant.parse("2026-11-05T09:00:00Z")),
            f.history.imported(beta, f.day(18), importedAt = Instant.parse("2026-11-22T23:30:00Z")),
        )

        val pattern = WeekdayPattern.of(f.facts(StatsRange.Week))

        assertEquals(listOf(Day(DayOfWeek.WEDNESDAY, 30_000, 2)), pattern.days)
    }

    @Test
    fun `all games sum the dailies, one game reads its own`() = runTest {
        f.history.store(
            f.history.solve(alpha, f.day(18), time = 40_000),
            f.history.solve(beta, f.day(18), time = 80_000),
        )

        val all = WeekdayPattern.of(f.facts(StatsRange.Week))
        val onlyBeta = WeekdayPattern.of(f.facts(StatsRange.Week, scope = beta))

        assertEquals(listOf(Day(DayOfWeek.WEDNESDAY, 60_000, 2)), all.days)
        assertEquals(listOf(Day(DayOfWeek.WEDNESDAY, 80_000, 1)), onlyBeta.days)
    }

    @Test
    fun `only the range's days count`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(1, month = 10)), f.history.solve(alpha, f.day(3)))

        val pattern = WeekdayPattern.of(f.facts(StatsRange.Month))

        assertEquals(listOf(DayOfWeek.TUESDAY), pattern.days.map { it.weekday })
    }

    @Test
    fun `no solve leaves the section out`() = runTest {
        val facts = f.facts(StatsRange.Month)

        WeekdayStatsSectionProvider.section(f.input(facts, StatsRange.Month)).test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a solve gives a ready section`() = runTest {
        f.history.store(f.history.solve(alpha, f.day(18), time = 55_000))
        val facts = f.facts(StatsRange.Week)

        WeekdayStatsSectionProvider.section(f.input(facts, StatsRange.Week)).test {
            val section = awaitItem() as StatsSection.Weekday
            assertEquals(listOf(Day(DayOfWeek.WEDNESDAY, 55_000, 1)), (section.state as SectionState.Ready).value.days)
            awaitComplete()
        }
    }
}
