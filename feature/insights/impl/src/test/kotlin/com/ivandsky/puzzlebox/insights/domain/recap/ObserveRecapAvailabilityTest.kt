package com.ivandsky.puzzlebox.insights.domain.recap

import app.cash.turbine.test
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveRecapAvailabilityTest {

    private val fixture = RecapHistoryFixture()
    private val availability = fixture.availability()

    private fun month(year: Int, month: Int) = RecapPeriod.Month(YearMonth.of(year, month))

    private fun year(year: Int) = RecapPeriod.Year(year)

    private suspend fun listed() = availability().first()

    @Test
    fun `no solves lists no month`() = runTest {
        availability().test {
            assertEquals(emptyList<RecapPeriod>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a month with a counted solve is listed newest first, each month once`() = runTest {
        fixture.store(
            fixture.solve(alpha, LocalDate.of(2026, 9, 3)),
            fixture.solve(alpha, LocalDate.of(2026, 11, 1)),
            fixture.solve(beta, LocalDate.of(2026, 11, 20)),
            fixture.imported(alpha, LocalDate.of(2025, 12, 31)),
        )

        availability().test {
            assertEquals(listOf(year(2025), month(2026, 11), month(2026, 9), month(2025, 12)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a month with only play again or practice has no recap`() = runTest {
        fixture.store(
            fixture.solve(alpha, LocalDate.of(2026, 10, 1)),
            fixture.playAgain(alpha, LocalDate.of(2026, 10, 1)),
            fixture.playAgain(beta, LocalDate.of(2026, 9, 5)),
            fixture.solve(beta, LocalDate.of(2026, 8, 5), puzzleNumber = null),
        )

        availability().test {
            assertEquals(listOf(month(2026, 10)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a puzzle imported late is listed in the month it was played`() = runTest {
        fixture.store(fixture.imported(alpha, LocalDate.of(2025, 3, 4)))

        availability().test {
            assertEquals(listOf(year(2025), month(2025, 3)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new solve adds its month`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 10, 1)))

        availability().test {
            assertEquals(listOf(month(2026, 10)), awaitItem())
            fixture.store(fixture.solve(alpha, LocalDate.of(2026, 10, 1)), fixture.solve(alpha, LocalDate.of(2026, 11, 1)))
            assertEquals(listOf(month(2026, 11), month(2026, 10)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the current year is not listed on Dec 19`() = runTest {
        fixture.calendar.setAll(LocalDate.of(2026, 12, 19))
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 12, 1)), fixture.solve(alpha, LocalDate.of(2026, 3, 1)))

        assertEquals(listOf(month(2026, 12), month(2026, 3)), listed())
    }

    @Test
    fun `the current year is listed from Dec 20`() = runTest {
        fixture.calendar.setAll(LocalDate.of(2026, 12, 20))
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 12, 1)), fixture.solve(alpha, LocalDate.of(2026, 3, 1)))

        assertEquals(listOf(year(2026), month(2026, 12), month(2026, 3)), listed())
    }

    @Test
    fun `a past year from imports is listed at any time of year, newest first`() = runTest {
        fixture.calendar.setAll(LocalDate.of(2026, 6, 1))
        fixture.store(fixture.imported(alpha, LocalDate.of(2024, 5, 1)), fixture.imported(beta, LocalDate.of(2025, 5, 1)))

        assertEquals(listOf(year(2025), year(2024), month(2025, 5), month(2024, 5)), listed())
    }

    @Test
    fun `a year with only play again has no recap`() = runTest {
        fixture.calendar.setAll(LocalDate.of(2027, 2, 1))
        fixture.store(fixture.playAgain(alpha, LocalDate.of(2026, 5, 1)))

        assertEquals(emptyList<RecapPeriod>(), listed())
    }
}
