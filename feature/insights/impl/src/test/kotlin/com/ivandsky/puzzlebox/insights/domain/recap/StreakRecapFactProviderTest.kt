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
    fun `the card holds the streak's length, dates, frozen days and the period the dial draws`() = runTest {
        val streak = StreakRun(day(3), day(9), setOf(day(5)))

        assertEquals(
            listOf(RecapCard.Streak(days = 7, from = day(3), to = day(9), freezesSpent = 2, frozenDays = setOf(day(5)), span = period)),
            StreakRecapFactProvider.cards(input(streak, freezesSpent = 2)),
        )
    }

    @Test
    fun `the freezes spent count the whole period while the frozen days are the longest streak's own`() = runTest {
        // Two freezes were spent in the period, but only one of them held the longest streak.
        val streak = StreakRun(day(10), day(20), setOf(day(12)))

        val card = StreakRecapFactProvider.cards(input(streak, freezesSpent = 2)).single() as RecapCard.Streak

        assertEquals(2, card.freezesSpent)
        assertEquals(setOf(day(12)), card.frozenDays)
    }

    @Test
    fun `a one-day streak is a card too`() = runTest {
        val streak = StreakRun(day(9), day(9), emptySet())

        assertEquals(
            listOf(RecapCard.Streak(days = 1, from = day(9), to = day(9), freezesSpent = 0, frozenDays = emptySet(), span = period)),
            StreakRecapFactProvider.cards(input(streak)),
        )
    }
}
