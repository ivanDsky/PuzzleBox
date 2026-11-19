package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.insights.domain.stats.DayRange
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The longest hint-free run and the solve behind the longest streak, over a day range and a month alike. */
class ComputePeriodFactsRecordsTest {

    private val fixture = RecapHistoryFixture()

    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 11, dayOfMonth)

    private suspend fun facts(first: Int, last: Int, scope: com.ivandsky.puzzlebox.model.GameId? = null) =
        fixture.computePeriodFacts(fixture.history(), DayRange(day(first), day(last)), scope)

    @Test
    fun `no solve has no run and no streak solve`() = runTest {
        val facts = facts(1, 30)

        assertNull(facts.longestHintFreeRun)
        assertNull(facts.longestStreakSolve)
    }

    @Test
    fun `a hint ends the run and the longest one is kept, the earlier on a tie`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(1)),
            fixture.solve(alpha, day(2)),
            fixture.solve(alpha, day(3), hints = 1),
            fixture.solve(alpha, day(4)),
            fixture.solve(alpha, day(5)),
            fixture.solve(alpha, day(6)),
            fixture.solve(alpha, day(7), hints = 2),
            fixture.solve(alpha, day(8)),
            fixture.solve(alpha, day(9)),
            fixture.solve(alpha, day(10)),
        )

        val run = checkNotNull(facts(1, 30).longestHintFreeRun)

        assertEquals(3, run.solves)
        assertEquals(day(4), run.start)
        assertEquals(day(6), run.last.day)
    }

    @Test
    fun `a run goes across games by puzzle day then game id`() = runTest {
        fixture.store(
            fixture.solve(beta, day(1)),
            fixture.solve(alpha, day(1)),
            fixture.solve(alpha, day(2)),
        )

        val run = checkNotNull(facts(1, 30).longestHintFreeRun)

        assertEquals(3, run.solves)
        assertEquals(alpha, run.last.game)
        assertEquals(day(2), run.last.day)
    }

    @Test
    fun `solves outside the range or not counted do not join a run`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(1)),
            fixture.solve(alpha, day(2), puzzleNumber = null),
            fixture.solve(alpha, day(3)),
            fixture.solve(alpha, day(20)),
        )

        assertEquals(2, checkNotNull(facts(1, 10).longestHintFreeRun).solves)
    }

    @Test
    fun `an import with a hint ends a run and one without does not`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(1)),
            fixture.imported(alpha, day(2)).copy(hints = 0),
            fixture.solve(alpha, day(3)),
        )

        val run = checkNotNull(facts(1, 30).longestHintFreeRun)

        assertEquals(3, run.solves)
        assertFalse(run.last.hasReplay.not())
    }

    @Test
    fun `the streak solve is the quickest of the streak's last day, and an import has no replay`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(4), time = 80_000),
            fixture.solve(alpha, day(5), time = 90_000),
            fixture.solve(beta, day(5), time = 40_000, puzzleNumber = 7),
        )

        val solve = checkNotNull(facts(1, 30).longestStreakSolve)

        assertEquals(beta, solve.game)
        assertEquals(day(5), solve.day)
        assertEquals(7, solve.puzzleNumber)
        assertTrue(solve.hasReplay)
    }

    @Test
    fun `an imported streak solve has no replay`() = runTest {
        fixture.store(fixture.imported(alpha, day(4)))

        assertFalse(checkNotNull(facts(1, 30).longestStreakSolve).hasReplay)
    }

    @Test
    fun `a range cuts the streak and its solve to its own days`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(4)),
            fixture.solve(alpha, day(5)),
            fixture.solve(alpha, day(6)),
        )

        val facts = facts(1, 5)

        assertEquals(2, checkNotNull(facts.longestStreak).days)
        assertEquals(day(5), checkNotNull(facts.longestStreakSolve).day)
    }
}
