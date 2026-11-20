package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hint-free, total time and Moments of a period, over a fixed multi-month history that includes imports. */
class ComputePeriodFactsMomentsTest {

    private val fixture = RecapHistoryFixture()
    private val october = RecapPeriod.Month(YearMonth.of(2026, 10))
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private val PeriodFacts.kinds get() = moments.map { it.moment }

    private suspend fun facts(period: RecapPeriod, scope: GameId? = null) =
        fixture.computePeriodFacts(fixture.history(), period, scope)

    @Test
    fun `no solves is no hint-free, no time, no moments`() = runTest {
        val facts = facts(november)

        assertEquals(0, facts.hintFree)
        assertEquals(0L, facts.totalTimeMs)
        assertEquals(emptyList<Moment>(), facts.kinds)
    }

    @Test
    fun `hint-free and total time count the period's counted solves, imports included, play again and practice left out`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 3), time = 390_000, hints = 0),
            fixture.solve(beta, day(11, 4), time = 200_000, hints = 2),
            // An import carries its hints as 0 or 1 and its LinkedIn time.
            fixture.imported(alpha, day(11, 5)).copy(time = 60_000, hints = 1),
            fixture.imported(beta, day(11, 6)).copy(time = 45_000, hints = 0),
            fixture.playAgain(alpha, day(11, 3)).copy(time = 1_000),
            fixture.solve(beta, day(11, 8), time = 2_000, puzzleNumber = null),
            fixture.solve(alpha, day(10, 20), time = 9_000_000),
        )

        val facts = facts(november)

        assertEquals(4, facts.solved)
        assertEquals(2, facts.hintFree)
        assertEquals(390_000L + 200_000 + 60_000 + 45_000, facts.totalTimeMs)
    }

    @Test
    fun `a puzzle imported late counts in its own period`() = runTest {
        fixture.store(fixture.imported(alpha, LocalDate.of(2025, 3, 4)).copy(time = 70_000))

        assertEquals(0L, facts(november).totalTimeMs)
        assertEquals(70_000L, facts(RecapPeriod.Year(2025)).totalTimeMs)
        assertEquals(1, facts(RecapPeriod.Year(2025)).hintFree)
    }

    @Test
    fun `a personal best in month 2 is relative to month 1, not to the month alone`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(10, 5), time = 380_000),
            fixture.solve(alpha, day(11, 3), time = 390_000),
            fixture.solve(alpha, day(11, 10), time = 385_000),
            fixture.solve(alpha, day(11, 17), time = 375_000),
        )

        // Nov 3 and Nov 10 are slower than October's 380 s; only Nov 17 beats it. Folding November alone would also call Nov 10 a best.
        assertEquals(listOf<Moment>(Moment.PersonalBest), facts(november).kinds)
        assertEquals(emptyList<Moment>(), facts(october).kinds)
    }

    @Test
    fun `a first sub-X is relative to the whole history`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(10, 5), time = 100_000),
            fixture.solve(alpha, day(11, 3), time = 110_000),
            fixture.solve(alpha, day(11, 10), time = 50_000),
        )

        // 1:40 went under 2:00 in October; November's 1:50 is under it again, so it is no first. 0:50 is the first under 1:00.
        assertEquals(listOf<Moment>(Moment.FirstSubX(120_000)), facts(october).kinds.filterIsInstance<Moment.FirstSubX>())
        assertEquals(
            listOf<Moment>(Moment.FirstSubX(60_000)),
            facts(november).kinds.filterIsInstance<Moment.FirstSubX>(),
        )
    }

    @Test
    fun `a full set and a streak milestone are earned, friend moments never are`() = runTest {
        fixture.store(
            *(1..7).flatMap { dayOfMonth ->
                listOf(
                    fixture.solve(alpha, day(11, dayOfMonth), time = 400_000 + dayOfMonth * 10_000L, puzzleNumber = 100 + dayOfMonth),
                    fixture.solve(beta, day(11, dayOfMonth), time = 400_000 + dayOfMonth * 10_000L, puzzleNumber = 100 + dayOfMonth),
                )
            }.toTypedArray(),
        )

        val moments = facts(november).kinds

        assertEquals(7, moments.count { it is Moment.FullSetDone })
        assertEquals(listOf(Moment.Streak(7)), moments.filterIsInstance<Moment.Streak>())
        // Slower every day: the first solve of each game is no best and nothing after it beats it.
        assertEquals(0, moments.count { it is Moment.PersonalBest })
        assertTrue(moments.none { it is Moment.TopThreeFriends || it is Moment.PassedFriend })
    }

    @Test
    fun `an import is a solve to the moments too`() = runTest {
        fixture.store(
            fixture.imported(alpha, day(11, 3)).copy(time = 400_000),
            fixture.imported(alpha, day(11, 5)).copy(time = 350_000),
        )

        assertEquals(listOf<Moment>(Moment.PersonalBest), facts(november).kinds)
    }

    @Test
    fun `a scope keeps one game's hint-free, time and moments, while the set still needs every game`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 3), time = 400_000),
            fixture.solve(beta, day(11, 3), time = 300_000, hints = 1),
        )

        val beta = facts(november, beta)
        val alpha = facts(november, alpha)

        assertEquals(0, beta.hintFree)
        assertEquals(300_000L, beta.totalTimeMs)
        assertEquals(1, alpha.hintFree)
        assertEquals(400_000L, alpha.totalTimeMs)
        // Beta finished the day's set: a scope shows the game's own moments but the set came from both games.
        assertEquals(listOf<Moment>(Moment.FullSetDone(Moment.FullSetDone.DayTotal(700_000, 1))), beta.kinds)
        assertEquals(emptyList<Moment>(), alpha.kinds)
    }

    @Test
    fun `each moment carries the day and game of the solve that earned it, oldest day first`() = runTest {
        fixture.store(
            fixture.solve(beta, day(11, 9), time = 300_000),
            fixture.solve(alpha, day(11, 3), time = 400_000),
            fixture.solve(alpha, day(11, 20), time = 350_000),
        )

        // The history is stored out of order; the card needs the stickers in the order they were earned.
        assertEquals(
            listOf(Moment.PersonalBest to day(11, 20)),
            facts(november).moments.map { it.moment to it.day }.filter { it.first is Moment.PersonalBest },
        )
        assertEquals(listOf(alpha), facts(november).moments.filter { it.moment is Moment.PersonalBest }.map { it.game })
        assertEquals(facts(november).moments.map { it.day }.sorted(), facts(november).moments.map { it.day })
    }
}
