package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class YearFinaleRecapFactProviderTest {

    private fun month(number: Int, solved: Int, game: com.ivandsky.puzzlebox.model.GameId = alpha, timeMs: Long = 60_000) =
        MonthFacts(YearMonth.of(2026, number), solved, game, timeMs)

    private fun input(
        period: RecapPeriod,
        months: List<MonthFacts>,
        byGame: Map<com.ivandsky.puzzlebox.model.GameId, Int> = mapOf(alpha to months.sumOf { it.solved }),
    ) = RecapInput(
        facts = PeriodFacts(
            period, scope = null, solved = months.sumOf { it.solved }, solvedByGame = byGame,
            fastestByGame = emptyMap(), longestStreak = null, freezesSpent = 0,
            hintFree = 0, totalTimeMs = 0, moments = emptyList(), months = months,
        ),
        games = listOf(alpha, beta),
    )

    private suspend fun finale(input: RecapInput) = YearFinaleRecapFactProvider.cards(input).single() as RecapCard.YearFinale

    @Test
    fun `the finale lists all twelve months with the empty ones at zero in calendar order`() = runTest {
        val card = finale(input(RecapPeriod.Year(2026), listOf(month(5, 4), month(3, 12))))

        assertEquals((1..12).map { YearMonth.of(2026, it) }, card.months.map { it.month })
        assertEquals(listOf(0, 0, 12, 0, 4, 0, 0, 0, 0, 0, 0, 0), card.months.map { it.solved })
        assertEquals(null, card.months[0].game)
        assertEquals(alpha, card.months[2].game)
        assertEquals(16, card.total)
    }

    @Test
    fun `the best month is the busiest and an equal count goes to the earlier month`() = runTest {
        val card = finale(input(RecapPeriod.Year(2026), listOf(month(2, 9), month(6, 20), month(8, 20))))

        assertEquals(YearMonth.of(2026, 6), card.bestMonth)
        assertEquals(20, card.bestMonthSolved)
    }

    @Test
    fun `the fastest solve is the quickest of the months and an equal time goes to the earlier month`() = runTest {
        val card = finale(input(RecapPeriod.Year(2026), listOf(month(2, 1, alpha, 40_000), month(7, 1, beta, 19_000), month(9, 1, alpha, 19_000))))

        assertEquals(YearMonth.of(2026, 7), card.fastestMonth)
        assertEquals(beta, card.fastestGame)
        assertEquals(19_000L, card.fastestTimeMs)
    }

    @Test
    fun `the most played game with an equal count is the one the app lists first`() = runTest {
        val card = finale(input(RecapPeriod.Year(2026), listOf(month(1, 8)), byGame = mapOf(beta to 4, alpha to 4)))

        assertEquals(alpha, card.mostPlayedGame)
        assertEquals(4, card.mostPlayedSolved)
    }

    @Test
    fun `a year of imports says how many days it covers`() = runTest {
        val facts = input(RecapPeriod.Year(2025), listOf(month(3, 2)))
        val card = finale(facts.copy(facts = facts.facts.copy(importedDays = 2)))

        assertEquals(2, card.importedDays)
    }

    @Test
    fun `a year with no counted solve has no finale`() = runTest {
        assertEquals(emptyList<RecapCard>(), YearFinaleRecapFactProvider.cards(input(RecapPeriod.Year(2026), emptyList(), byGame = emptyMap())))
    }

    @Test
    fun `a month period has none`() = runTest {
        assertEquals(emptyList<RecapCard>(), YearFinaleRecapFactProvider.cards(input(RecapPeriod.Month(YearMonth.of(2026, 3)), listOf(month(3, 5)))))
    }
}
