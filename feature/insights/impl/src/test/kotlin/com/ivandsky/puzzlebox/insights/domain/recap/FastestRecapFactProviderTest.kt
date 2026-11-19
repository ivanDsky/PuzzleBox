package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FastestRecapFactProviderTest {

    private val period = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val gone = GameId("retired")

    private fun solve(day: Int, timeMs: Long = 60_000, replay: Boolean = true) =
        FastestSolve(LocalDate.of(2026, 11, day), puzzleNumber = 100 + day, timeMs = timeMs, hasReplay = replay)

    private fun input(fastest: Map<GameId, FastestSolve>, games: List<GameId> = listOf(alpha, beta)) = RecapInput(
        facts = PeriodFacts(
            period, scope = null, solved = fastest.size, solvedByGame = fastest.mapValues { 1 },
            fastestByGame = fastest, longestStreak = null, freezesSpent = 0,
            hintFree = 0, totalTimeMs = 0, moments = emptyList(),
        ),
        games = games,
    )

    private fun row(game: GameId, solve: FastestSolve) =
        RecapCard.Fastest.Row(game, solve.timeMs, solve.day, solve.puzzleNumber, solve.hasReplay)

    @Test
    fun `a period with no solves has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), FastestRecapFactProvider.cards(input(emptyMap())))
    }

    @Test
    fun `the card holds each game's fastest in the app's order`() = runTest {
        val a = solve(3, 58_000)
        val b = solve(9, 141_000, replay = false)

        val cards = FastestRecapFactProvider.cards(input(linkedMapOf(beta to b, alpha to a)))

        assertEquals(listOf(RecapCard.Fastest(listOf(row(alpha, a), row(beta, b)))), cards)
    }

    @Test
    fun `a game with no solve has no row`() = runTest {
        val b = solve(4)

        assertEquals(
            listOf(RecapCard.Fastest(listOf(row(beta, b)))),
            FastestRecapFactProvider.cards(input(mapOf(beta to b))),
        )
    }

    @Test
    fun `a game the app no longer lists follows the listed ones`() = runTest {
        val g = solve(1)
        val a = solve(2)

        assertEquals(
            listOf(RecapCard.Fastest(listOf(row(alpha, a), row(gone, g)))),
            FastestRecapFactProvider.cards(input(mapOf(gone to g, alpha to a))),
        )
    }
}
