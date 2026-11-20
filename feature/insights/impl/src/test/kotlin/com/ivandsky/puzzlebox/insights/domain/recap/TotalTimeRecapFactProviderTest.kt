package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TotalTimeRecapFactProviderTest {

    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))

    private val days = listOf(
        DayTally(LocalDate.of(2026, 11, 3), solved = 30, timeMs = 8_000_000, hintFree = 28),
        DayTally(LocalDate.of(2026, 11, 9), solved = 12, timeMs = 3_535_000, hintFree = 8),
    )

    private fun input(solved: Int, totalMs: Long, days: List<DayTally> = emptyList(), period: RecapPeriod = november) = RecapInput(
        facts = emptyFacts(period).copy(solved = solved, totalTimeMs = totalMs, dayTallies = days),
        games = emptyList(),
    )

    @Test
    fun `a period with no counted solve has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), TotalTimeRecapFactProvider.cards(input(solved = 0, totalMs = 0)))
    }

    @Test
    fun `the card holds the total time, the solves it went on and the days the river draws`() = runTest {
        assertEquals(
            listOf(RecapCard.TotalTime(totalMs = 11_535_000, solved = 42, days = days, period = november)),
            TotalTimeRecapFactProvider.cards(input(solved = 42, totalMs = 11_535_000, days = days)),
        )
    }

    @Test
    fun `a year's card carries the year, so its river draws weeks`() = runTest {
        val year = RecapPeriod.Year(2026)

        val card = TotalTimeRecapFactProvider.cards(input(solved = 42, totalMs = 11_535_000, days = days, period = year)).single()

        assertEquals(year, (card as RecapCard.TotalTime).period)
    }
}
