package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SolvedRecapFactProviderTest {

    private val period = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val gone = GameId("retired")

    private fun input(solved: Map<GameId, Int>, games: List<GameId> = listOf(alpha, beta)) = RecapInput(
        facts = PeriodFacts(
            period, scope = null, solved = solved.values.sum(), solvedByGame = solved,
            fastestByGame = emptyMap(), longestStreak = null, freezesSpent = 0,
        ),
        games = games,
    )

    @Test
    fun `a period with no solves has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), SolvedRecapFactProvider.cards(input(emptyMap())))
    }

    @Test
    fun `the card holds the total and each game in the app's order`() = runTest {
        // The history lists beta first; the card follows the app's order, alpha first.
        val cards = SolvedRecapFactProvider.cards(input(linkedMapOf(beta to 4, alpha to 9)))

        assertEquals(
            listOf(RecapCard.Solved(13, listOf(RecapCard.Solved.PerGame(alpha, 9), RecapCard.Solved.PerGame(beta, 4)))),
            cards,
        )
    }

    @Test
    fun `a game with no solve has no row`() = runTest {
        val cards = SolvedRecapFactProvider.cards(input(mapOf(beta to 2)))

        assertEquals(listOf(RecapCard.Solved(2, listOf(RecapCard.Solved.PerGame(beta, 2)))), cards)
    }

    @Test
    fun `a game the app no longer lists still adds to its rows, after the listed ones`() = runTest {
        val cards = SolvedRecapFactProvider.cards(input(mapOf(gone to 1, alpha to 2)))

        assertEquals(
            listOf(RecapCard.Solved(3, listOf(RecapCard.Solved.PerGame(alpha, 2), RecapCard.Solved.PerGame(gone, 1)))),
            cards,
        )
    }
}
