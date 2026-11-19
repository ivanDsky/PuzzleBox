package com.ivandsky.puzzlebox.insights.domain.recap

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TotalTimeRecapFactProviderTest {

    private fun input(solved: Int, totalMs: Long) = RecapInput(
        facts = emptyFacts().copy(solved = solved, totalTimeMs = totalMs),
        games = emptyList(),
    )

    @Test
    fun `a period with no counted solve has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), TotalTimeRecapFactProvider.cards(input(solved = 0, totalMs = 0)))
    }

    @Test
    fun `the card holds the total time and the solves it went on`() = runTest {
        assertEquals(
            listOf(RecapCard.TotalTime(totalMs = 11_535_000, solved = 42)),
            TotalTimeRecapFactProvider.cards(input(solved = 42, totalMs = 11_535_000)),
        )
    }
}
