package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HintFreeRecapFactProviderTest {

    private fun input(solved: Int, hintFree: Int) = RecapInput(
        facts = emptyFacts().copy(solved = solved, hintFree = hintFree),
        games = emptyList(),
    )

    @Test
    fun `no hint-free solve has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), HintFreeRecapFactProvider.cards(input(solved = 5, hintFree = 0)))
    }

    @Test
    fun `no solve has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), HintFreeRecapFactProvider.cards(input(solved = 0, hintFree = 0)))
    }

    @Test
    fun `the card holds the hint-free count and the solves it is out of`() = runTest {
        assertEquals(listOf(RecapCard.HintFree(hintFree = 36, solved = 42)), HintFreeRecapFactProvider.cards(input(solved = 42, hintFree = 36)))
    }
}

internal fun emptyFacts(period: RecapPeriod = RecapPeriod.Month(YearMonth.of(2026, 11))) = PeriodFacts(
    period, scope = null, solved = 0, solvedByGame = emptyMap(), fastestByGame = emptyMap(), longestStreak = null,
    freezesSpent = 0, hintFree = 0, totalTimeMs = 0, moments = emptyList(),
)
