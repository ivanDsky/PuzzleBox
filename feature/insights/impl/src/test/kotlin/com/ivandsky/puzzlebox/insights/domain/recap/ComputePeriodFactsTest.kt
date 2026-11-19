package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The period facts over a fixed history: what counts, and which period a solve lands in. */
class ComputePeriodFactsTest {

    private val fixture = RecapHistoryFixture()
    private val compute = ComputePeriodFacts()
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))

    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)

    private suspend fun facts(period: RecapPeriod, scope: GameId? = null) =
        compute(fixture.history(), period, scope)

    @Test
    fun `no solves is zero`() = runTest {
        assertEquals(PeriodFacts(november, scope = null, solved = 0, solvedByGame = emptyMap()), facts(november))
    }

    @Test
    fun `counts the period's solves in total and per game`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1)),
            fixture.solve(alpha, day(11, 2)),
            fixture.solve(beta, day(11, 2)),
            fixture.solve(alpha, day(10, 31)),
        )

        assertEquals(
            PeriodFacts(november, scope = null, solved = 3, solvedByGame = mapOf(alpha to 2, beta to 1)),
            facts(november),
        )
    }

    @Test
    fun `a scope counts one game only`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(beta, day(11, 1)), fixture.solve(beta, day(11, 2)))

        assertEquals(
            PeriodFacts(november, scope = beta, solved = 2, solvedByGame = mapOf(beta to 2)),
            facts(november, beta),
        )
    }

    @Test
    fun `play again never counts`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1)),
            fixture.playAgain(alpha, day(11, 1)),
            // A day with only a later play has no record row at all.
            fixture.playAgain(beta, day(11, 2)),
        )

        assertEquals(1, facts(november).solved)
    }

    @Test
    fun `practice plays never count`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(alpha, day(11, 2), puzzleNumber = null))

        assertEquals(1, facts(november).solved)
    }

    @Test
    fun `imports count`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.imported(beta, day(11, 2)))

        assertEquals(2, facts(november).solved)
    }

    @Test
    fun `a 2025 puzzle imported in November 2026 lands in 2025`() = runTest {
        fixture.store(fixture.imported(alpha, day(11, 20, year = 2025), importedAt = Instant.parse("2026-11-05T09:00:00Z")))

        assertEquals(0, facts(november).solved)
        assertEquals(1, facts(RecapPeriod.Month(YearMonth.of(2025, 11))).solved)
        assertEquals(1, facts(RecapPeriod.Year(2025)).solved)
        assertEquals(0, facts(RecapPeriod.Year(2026)).solved)
    }

    @Test
    fun `a month holds its first and last day only`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(10, 31)),
            fixture.solve(alpha, day(11, 1)),
            fixture.solve(alpha, day(11, 30)),
            fixture.solve(alpha, day(12, 1)),
        )

        assertEquals(2, facts(november).solved)
    }

    @Test
    fun `the years add up to the game page's solved count`() = runTest {
        fixture.store(
            fixture.imported(alpha, day(3, 4, year = 2025)),
            fixture.imported(beta, day(3, 4, year = 2025)),
            fixture.solve(alpha, day(11, 1)),
            fixture.solve(beta, day(11, 1)),
            fixture.solve(alpha, day(11, 2)),
            fixture.playAgain(alpha, day(11, 2)),
            fixture.solve(alpha, day(11, 3), puzzleNumber = null),
        )
        val stats = ObserveGameStats(
            fixture.registry,
            fixture.observeHistory,
            ComputeStreak(GetPuzzleDay(fixture.registry, Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC))),
        )

        assertEquals(stats().first().solved, facts(RecapPeriod.Year(2025)).solved + facts(RecapPeriod.Year(2026)).solved)
        assertEquals(stats(alpha).first().solved, facts(RecapPeriod.Year(2025), alpha).solved + facts(RecapPeriod.Year(2026), alpha).solved)
        assertEquals(stats(beta).first().solved, facts(RecapPeriod.Year(2025), beta).solved + facts(RecapPeriod.Year(2026), beta).solved)
    }
}
