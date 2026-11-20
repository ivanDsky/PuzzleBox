package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthsRecapFactProviderTest {

    private val march = MonthFacts(YearMonth.of(2026, 3), solved = 12, fastestGame = alpha, fastestTimeMs = 58_000, bestGame = beta, bestTimeMs = 61_000, underAveragePercent = 23)
    private val may = MonthFacts(YearMonth.of(2026, 5), solved = 1, fastestGame = beta, fastestTimeMs = 141_000, bestGame = beta, bestTimeMs = 141_000, underAveragePercent = 0)

    private fun input(period: RecapPeriod, months: List<MonthFacts>) = RecapInput(
        facts = PeriodFacts(
            period, scope = null, solved = months.sumOf { it.solved }, solvedByGame = emptyMap(),
            fastestByGame = emptyMap(), longestStreak = null, freezesSpent = 0,
            hintFree = 0, totalTimeMs = 0, moments = emptyList(), months = months,
        ),
        games = listOf(alpha, beta),
    )

    @Test
    fun `a year has one card per month with its count and its fastest solve`() = runTest {
        assertEquals(
            listOf(
                RecapCard.Month(YearMonth.of(2026, 3), solved = 12, bestGame = beta, bestTimeMs = 61_000, underAveragePercent = 23),
                RecapCard.Month(YearMonth.of(2026, 5), solved = 1, bestGame = beta, bestTimeMs = 141_000, underAveragePercent = 0),
            ),
            MonthsRecapFactProvider.cards(input(RecapPeriod.Year(2026), listOf(march, may))),
        )
    }

    @Test
    fun `a year with no counted solve has no month card`() = runTest {
        assertEquals(emptyList<RecapCard>(), MonthsRecapFactProvider.cards(input(RecapPeriod.Year(2026), emptyList())))
    }

    @Test
    fun `a month period has none`() = runTest {
        assertEquals(emptyList<RecapCard>(), MonthsRecapFactProvider.cards(input(RecapPeriod.Month(YearMonth.of(2026, 3)), listOf(march))))
    }
}
