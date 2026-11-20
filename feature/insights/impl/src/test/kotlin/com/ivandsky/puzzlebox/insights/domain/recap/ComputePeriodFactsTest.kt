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
import org.junit.Assert.assertNull
import org.junit.Test

/** The period facts over a fixed history: what counts, and which period a solve lands in. */
class ComputePeriodFactsTest {

    private val fixture = RecapHistoryFixture()
    private val compute = fixture.computePeriodFacts
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))

    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)

    private suspend fun facts(period: RecapPeriod, scope: GameId? = null) =
        compute(fixture.history(), period, scope)

    @Test
    fun `no solves is zero`() = runTest {
        assertEquals(
            PeriodFacts(
                november, scope = null, solved = 0, solvedByGame = emptyMap(),
                fastestByGame = emptyMap(), longestStreak = null, freezesSpent = 0,
                hintFree = 0, totalTimeMs = 0, moments = emptyList(),
            ),
            facts(november),
        )
    }

    @Test
    fun `counts the period's solves in total and per game`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1)),
            fixture.solve(alpha, day(11, 2)),
            fixture.solve(beta, day(11, 2)),
            fixture.solve(alpha, day(10, 31)),
        )

        val facts = facts(november)
        assertEquals(3, facts.solved)
        assertEquals(mapOf(alpha to 2, beta to 1), facts.solvedByGame)
    }

    @Test
    fun `a scope counts one game only`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(beta, day(11, 1)), fixture.solve(beta, day(11, 2)))

        val facts = facts(november, beta)
        assertEquals(beta, facts.scope)
        assertEquals(2, facts.solved)
        assertEquals(mapOf(beta to 2), facts.solvedByGame)
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

    // --- Fastest solve ---

    @Test
    fun `fastest is each game's quickest counted solve`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1), time = 90_000, puzzleNumber = 101),
            fixture.solve(alpha, day(11, 2), time = 61_000, puzzleNumber = 102),
            fixture.solve(alpha, day(11, 3), time = 75_000, puzzleNumber = 103),
            fixture.solve(beta, day(11, 4), time = 200_000, puzzleNumber = 104),
        )

        assertEquals(
            mapOf(
                alpha to FastestSolve(day(11, 2), puzzleNumber = 102, timeMs = 61_000, hasReplay = true),
                beta to FastestSolve(day(11, 4), puzzleNumber = 104, timeMs = 200_000, hasReplay = true),
            ),
            facts(november).fastestByGame,
        )
    }

    @Test
    fun `a tie on time goes to fewer hints, then the earlier day`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1), time = 60_000, hints = 2, puzzleNumber = 101),
            fixture.solve(alpha, day(11, 2), time = 60_000, hints = 1, puzzleNumber = 102),
            fixture.solve(alpha, day(11, 3), time = 60_000, hints = 1, puzzleNumber = 103),
        )

        assertEquals(day(11, 2), facts(november).fastestByGame.getValue(alpha).day)
    }

    @Test
    fun `fastest only looks inside the period`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(10, 31), time = 10_000, puzzleNumber = 99),
            fixture.solve(alpha, day(11, 1), time = 70_000, puzzleNumber = 100),
            fixture.solve(alpha, day(12, 1), time = 5_000, puzzleNumber = 130),
        )

        assertEquals(70_000L, facts(november).fastestByGame.getValue(alpha).timeMs)
    }

    @Test
    fun `an imported time counts and has no replay`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1), time = 80_000),
            fixture.imported(alpha, day(11, 2)).copy(time = 40_000),
        )

        val fastest = facts(november).fastestByGame.getValue(alpha)

        assertEquals(40_000L, fastest.timeMs)
        assertEquals(false, fastest.hasReplay)
    }

    @Test
    fun `the average is the floor of the mean of the counted solves, imports in and the rest out`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1), time = 40_001),
            fixture.imported(alpha, day(11, 2)).copy(time = 50_000),
            fixture.solve(alpha, day(11, 3), time = 60_000),
            fixture.playAgain(alpha, day(11, 3)).copy(time = 900_000),
            fixture.solve(alpha, day(10, 31), time = 900_000),
            fixture.solve(beta, day(11, 4), time = 70_000),
        )

        assertEquals(mapOf(alpha to 50_000L, beta to 70_000L), facts(november).averageTimeMsByGame)
    }

    @Test
    fun `an app solve has a replay`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)))

        assertEquals(true, facts(november).fastestByGame.getValue(alpha).hasReplay)
    }

    @Test
    fun `play again and practice never make the fastest`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1), time = 80_000),
            fixture.playAgain(alpha, day(11, 1)).copy(time = 1_000),
            fixture.solve(alpha, day(11, 2), time = 2_000, puzzleNumber = null),
        )

        assertEquals(80_000L, facts(november).fastestByGame.getValue(alpha).timeMs)
    }

    @Test
    fun `a scope keeps one game's fastest only`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(beta, day(11, 1)))

        assertEquals(setOf(beta), facts(november, beta).fastestByGame.keys)
    }

    // --- Longest streak ---

    private fun solveDays(game: GameId, from: Int, to: Int, month: Int = 11) =
        (from..to).map { fixture.solve(game, day(month, it), puzzleNumber = 100 + it) }

    @Test
    fun `no streak day in the period is no streak and no freeze`() = runTest {
        fixture.store(fixture.solve(alpha, day(10, 20)))

        val facts = facts(november)

        assertNull(facts.longestStreak)
        assertEquals(0, facts.freezesSpent)
    }

    @Test
    fun `the longest streak runs over the period`() = runTest {
        fixture.store(*(solveDays(alpha, 2, 4) + solveDays(alpha, 10, 16)).toTypedArray())

        val streak = facts(november).longestStreak

        assertEquals(StreakRun(day(11, 10), day(11, 16), emptySet()), streak)
        assertEquals(7, streak?.days)
    }

    @Test
    fun `days of different games join one streak`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 5)), fixture.solve(beta, day(11, 6)), fixture.solve(alpha, day(11, 7)))

        assertEquals(3, facts(november).longestStreak?.days)
    }

    @Test
    fun `a streak that began last month counts only this month's side`() = runTest {
        fixture.store(*solveDays(alpha, 27, 30, month = 10).toTypedArray(), *solveDays(alpha, 1, 3).toTypedArray(), fixture.solve(alpha, day(10, 31)))

        val streak = facts(november).longestStreak

        assertEquals(day(11, 1), streak?.start)
        assertEquals(day(11, 3), streak?.end)
        assertEquals(3, streak?.days)
    }

    @Test
    fun `a streak that runs on into next month counts only this month's side`() = runTest {
        fixture.store(*solveDays(alpha, 28, 30).toTypedArray(), fixture.solve(alpha, day(12, 1)), fixture.solve(alpha, day(12, 2)))

        val streak = facts(november).longestStreak

        assertEquals(StreakRun(day(11, 28), day(11, 30), emptySet()), streak)
        assertEquals(2, facts(RecapPeriod.Month(YearMonth.of(2026, 12))).longestStreak?.days)
    }

    @Test
    fun `a freeze holds the streak and counts as a streak day and a spent freeze`() = runTest {
        // Nov 3 has no solve; the walk freezes it, so Nov 1-5 is one five-day streak.
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(alpha, day(11, 2)), fixture.solve(alpha, day(11, 4)), fixture.solve(alpha, day(11, 5)))

        val facts = facts(november)

        assertEquals(StreakRun(day(11, 1), day(11, 5), setOf(day(11, 3))), facts.longestStreak)
        assertEquals(5, facts.longestStreak?.days)
        assertEquals(1, facts.freezesSpent)
    }

    @Test
    fun `freezes spent counts only frozen days inside the period`() = runTest {
        // One freeze on Oct 31 (last month) and one on Nov 2, in one streak running Oct 29 - Nov 4.
        fixture.store(
            fixture.solve(alpha, day(10, 29)),
            fixture.solve(alpha, day(10, 30)),
            fixture.solve(alpha, day(11, 1)),
            fixture.solve(alpha, day(11, 3)),
            fixture.solve(alpha, day(11, 4)),
        )

        assertEquals(setOf(day(11, 2)), facts(november).longestStreak?.frozen)
        assertEquals(1, facts(november).freezesSpent)
        assertEquals(1, facts(RecapPeriod.Month(YearMonth.of(2026, 10))).freezesSpent)
    }

    @Test
    fun `a frozen streak from the 28th of last month to the 3rd counts three days and only this month's freeze`() = runTest {
        // Oct 28-Nov 3 is one streak with Oct 30 and Nov 2 frozen; November holds Nov 1-3 and the Nov 2 freeze.
        fixture.store(
            fixture.solve(alpha, day(10, 28)),
            fixture.solve(alpha, day(10, 29)),
            fixture.solve(alpha, day(10, 31)),
            fixture.solve(alpha, day(11, 1)),
            fixture.solve(alpha, day(11, 3)),
        )

        val facts = facts(november)

        assertEquals(StreakRun(day(11, 1), day(11, 3), setOf(day(11, 2))), facts.longestStreak)
        assertEquals(3, facts.longestStreak?.days)
        assertEquals(1, facts.freezesSpent)
    }

    @Test
    fun `a late import changes the frozen days`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(alpha, day(11, 3)))
        assertEquals(1, facts(november).freezesSpent)

        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(alpha, day(11, 3)), fixture.imported(beta, day(11, 2)))

        assertEquals(0, facts(november).freezesSpent)
        assertEquals(3, facts(november).longestStreak?.days)
    }

    @Test
    fun `an archived solve is no streak day unless a LinkedIn import covers it`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(alpha, day(11, 2)), fixture.solve(alpha, day(11, 3)))
        fixture.captureArchived(alpha, day(11, 2))

        val facts = facts(november)

        assertEquals(3, facts.solved)
        // Nov 2 is archived: it is no streak day, so a freeze holds it; the run is still Nov 1-3, with Nov 2 frozen.
        assertEquals(setOf(day(11, 2)), facts.longestStreak?.frozen)
        assertEquals(1, facts.freezesSpent)
    }

    @Test
    fun `a scope counts one game's streak only`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 5)), fixture.solve(beta, day(11, 6)), fixture.solve(alpha, day(11, 7)), fixture.solve(alpha, day(11, 8)))

        assertEquals(1, facts(november, beta).longestStreak?.days)
        assertEquals(0, facts(november).freezesSpent)
        assertEquals(1, facts(november, alpha).freezesSpent)
        assertEquals(4, facts(november, alpha).longestStreak?.days)
    }

    @Test
    fun `a streak wholly in the month is the game page's best streak`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1)),
            fixture.solve(alpha, day(11, 2)),
            fixture.solve(alpha, day(11, 4)),
            fixture.solve(alpha, day(11, 5)),
            fixture.solve(alpha, day(11, 6)),
            fixture.solve(alpha, day(11, 10)),
        )
        val stats = ObserveGameStats(fixture.registry, fixture.observeHistory, fixture.computeStreak)

        assertEquals(stats().first().bestStreak, facts(november).longestStreak?.days)
        assertEquals(stats(alpha).first().bestStreak, facts(november, alpha).longestStreak?.days)
    }

    @Test
    fun `a year groups its counted solves by month with the month's fastest solve`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(3, 1), time = 90_000),
            fixture.solve(beta, day(3, 2), time = 40_000),
            fixture.solve(alpha, day(3, 3), time = 40_000, hints = 1),
            fixture.solve(alpha, day(5, 2), time = 70_000),
            fixture.playAgain(alpha, day(7, 1)),
            fixture.solve(alpha, day(12, 31, year = 2025)),
        )

        assertEquals(
            listOf(
                MonthFacts(YearMonth.of(2026, 3), 3, beta, 40_000, bestGame = alpha, bestTimeMs = 40_000, underAveragePercent = 40),
                MonthFacts(YearMonth.of(2026, 5), 1, alpha, 70_000, bestGame = alpha, bestTimeMs = 70_000, underAveragePercent = 0),
            ),
            facts(RecapPeriod.Year(2026)).months,
        )
    }

    @Test
    fun `a month's best is the game that beat its own year average most, not the absolute fastest`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(1, 1), time = 20_000),
            fixture.solve(alpha, day(2, 1), time = 20_000),
            fixture.solve(beta, day(1, 2), time = 100_000),
            fixture.solve(beta, day(2, 2), time = 200_000),
        )

        val january = facts(RecapPeriod.Year(2026)).months.first()
        assertEquals(alpha, january.fastestGame)
        assertEquals(beta, january.bestGame)
        assertEquals(100_000L, january.bestTimeMs)
        assertEquals(33, january.underAveragePercent)
    }

    @Test
    fun `a game with one solve in the year is skipped when another game has an average to beat`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(1, 1), time = 60_000),
            fixture.solve(alpha, day(2, 1), time = 30_000),
            fixture.solve(beta, day(1, 2), time = 10_000),
        )

        assertEquals(alpha, facts(RecapPeriod.Year(2026)).months.first().bestGame)
    }

    @Test
    fun `a game with one solve in the year is the month's best when it is the only game`() = runTest {
        fixture.store(fixture.solve(beta, day(3, 2), time = 50_000))

        val march = facts(RecapPeriod.Year(2026)).months.single()
        assertEquals(beta, march.bestGame)
        assertEquals(0, march.underAveragePercent)
    }

    @Test
    fun `equal ratios go to the game with more solves that month`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(1, 1), time = 10_000),
            fixture.solve(alpha, day(2, 1), time = 30_000),
            fixture.solve(beta, day(1, 2), time = 10_000),
            fixture.solve(beta, day(1, 3), time = 30_000),
        )

        assertEquals(beta, facts(RecapPeriod.Year(2026)).months.first().bestGame)
    }

    @Test
    fun `equal ratios and counts go to the game the app lists first`() = runTest {
        fixture.store(
            fixture.solve(beta, day(1, 1), time = 10_000),
            fixture.solve(beta, day(2, 1), time = 30_000),
            fixture.solve(alpha, day(1, 2), time = 10_000),
            fixture.solve(alpha, day(2, 2), time = 30_000),
        )

        assertEquals(alpha, facts(RecapPeriod.Year(2026)).months.first().bestGame)
    }

    @Test
    fun `a month period carries no month breakdown`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)))

        assertEquals(emptyList<MonthFacts>(), facts(november).months)
    }

    @Test
    fun `a year of imports alone counts its distinct puzzle days`() = runTest {
        fixture.store(
            fixture.imported(alpha, day(3, 1, year = 2025)),
            fixture.imported(beta, day(3, 1, year = 2025)),
            fixture.imported(alpha, day(4, 2, year = 2025)),
        )

        assertEquals(2, facts(RecapPeriod.Year(2025)).importedDays)
    }

    @Test
    fun `a year that holds an app solve says nothing about imported days`() = runTest {
        fixture.store(fixture.imported(alpha, day(3, 1)), fixture.solve(alpha, day(3, 2)))

        assertNull(facts(RecapPeriod.Year(2026)).importedDays)
        assertNull(facts(november).importedDays)
    }
}
