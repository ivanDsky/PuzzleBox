package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakRecapFactProviderTest {

    private val period = RecapPeriod.Month(YearMonth.of(2026, 11))

    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 11, dayOfMonth)

    private fun input(streak: StreakRun?, freezesSpent: Int = 0) = RecapInput(
        facts = PeriodFacts(
            period, scope = null, solved = 0, solvedByGame = emptyMap(),
            fastestByGame = emptyMap(), longestStreak = streak, freezesSpent = freezesSpent,
            hintFree = 0, totalTimeMs = 0, moments = emptyList(),
        ),
        games = emptyList(),
    )

    @Test
    fun `no streak in the period has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), StreakRecapFactProvider.cards(input(null)))
    }

    @Test
    fun `the card holds the streak's length, dates and the period's freezes`() = runTest {
        val streak = StreakRun(day(3), day(9), setOf(day(5)))

        assertEquals(
            listOf(RecapCard.Streak(days = 7, from = day(3), to = day(9), freezesSpent = 2)),
            StreakRecapFactProvider.cards(input(streak, freezesSpent = 2)),
        )
    }

    @Test
    fun `a one-day streak is a card too`() = runTest {
        val streak = StreakRun(day(9), day(9), emptySet())

        assertEquals(
            listOf(RecapCard.Streak(days = 1, from = day(9), to = day(9), freezesSpent = 0)),
            StreakRecapFactProvider.cards(input(streak)),
        )
    }
}
