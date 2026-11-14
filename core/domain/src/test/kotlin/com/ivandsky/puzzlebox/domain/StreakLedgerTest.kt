package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.domain.StreakFreezes.Moment
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The streak-freeze rule as [ComputeStreak.invoke] replays it with `StreakFreeze` on: the spec's
 * golden table row by row, its edges, and the day-by-day walk the gap-jumping pass must equal.
 * With the flag off the ledger is the strict streak and carries no freezes.
 */
class StreakLedgerTest {

    private val registry = TestGames.registry(listOf(GameId("zip")))
    private val freezeOn = FakeReleaseFlags(ReleaseFlag.StreakFreeze)

    // The golden table's history: day 1 is Jan 1; solved 1–7, 9–85, 88–115 and 121.
    private val goldenSolved = (1..7) + (9..85) + (88..115) + 121

    private fun day(n: Int): LocalDate = LocalDate.of(2026, 1, 1).plusDays(n - 1L)

    private fun dayNumber(day: LocalDate): Int = ChronoUnit.DAYS.between(day(1), day).toInt() + 1

    // TestGames' puzzle zone is UTC, so noon UTC sits inside the puzzle day.
    private fun streakOn(today: LocalDate, flags: ReleaseFlags = freezeOn): ComputeStreak =
        streakAt(today.atTime(12, 0).toInstant(ZoneOffset.UTC), flags)

    private fun streakAt(now: Instant, flags: ReleaseFlags = freezeOn): ComputeStreak =
        ComputeStreak(GetPuzzleDay(registry, Clock.fixed(now, ZoneOffset.UTC)), flags)

    /** One golden-table row; [frozen] and the day arguments are the table's day numbers. */
    private data class Row(
        val streak: Int,
        val held: Int,
        val step: Int,
        val run: Int,
        val daysToNext: Int?,
        val frozen: Set<Int>,
        val best: Int,
    )

    /** The table's state once every solve up to [solvedThrough] has landed and the puzzle day is [today]. */
    private fun goldenRow(solvedThrough: Int, today: Int = solvedThrough): Row {
        val solved = goldenSolved.filter { it <= solvedThrough }.map(::day)
        val streak = streakOn(day(today))
        val ledger = streak(solved)
        val replay = streak.replay(solved)
        val freezes = checkNotNull(ledger.freezes)
        return Row(
            streak = ledger.current,
            held = freezes.held,
            step = replay.step,
            run = replay.run,
            daysToNext = freezes.daysToNext,
            frozen = freezes.frozenDays.mapTo(mutableSetOf(), ::dayNumber),
            best = ledger.best,
        )
    }

    private fun goldenMoment(solvedThrough: Int, today: Int = solvedThrough): Moment? =
        streakOn(day(today))(goldenSolved.filter { it <= solvedThrough }.map(::day)).freezes?.lastSolve

    @Test
    fun `no solves is an empty ledger seven days from a freeze`() {
        val expected = StreakLedger(0, 0, StreakFreezes(held = 0, frozenDays = emptySet(), daysToNext = 7, lastSolve = null))
        assertEquals(expected, streakOn(day(1))(emptyList()))
    }

    @Test
    fun `days 1 to 6 hold the starting two freezes`() {
        assertEquals(Row(streak = 6, held = 2, step = 0, run = 6, daysToNext = 1, frozen = emptySet(), best = 6), goldenRow(6))
    }

    @Test
    fun `day 7 completes step 0 and earns a freeze`() {
        assertEquals(Row(streak = 7, held = 3, step = 1, run = 0, daysToNext = 14, frozen = emptySet(), best = 7), goldenRow(7))
        assertEquals(Moment(day(7), spent = emptyList(), earned = true), goldenMoment(7))
    }

    @Test
    fun `missed day 8 is frozen, holds the count and steps the ladder back`() {
        val row = goldenRow(solvedThrough = 7, today = 9)
        assertEquals(Row(streak = 7, held = 2, step = 0, run = 0, daysToNext = 7, frozen = setOf(8), best = 7), row)
    }

    @Test
    fun `day 9 carries the freeze spent on day 8`() {
        assertEquals(Moment(day(9), spent = listOf(day(8)), earned = false), goldenMoment(9))
    }

    @Test
    fun `days 9 to 15 earn step 0 again`() {
        assertEquals(Row(streak = 14, held = 3, step = 1, run = 0, daysToNext = 14, frozen = setOf(8), best = 14), goldenRow(15))
        assertEquals(Moment(day(15), spent = emptyList(), earned = true), goldenMoment(15))
    }

    @Test
    fun `days 16 to 29 complete step 1 after fourteen in a row`() {
        assertEquals(Row(streak = 27, held = 3, step = 1, run = 13, daysToNext = 1, frozen = setOf(8), best = 27), goldenRow(28))
        assertEquals(Row(streak = 28, held = 4, step = 2, run = 0, daysToNext = 28, frozen = setOf(8), best = 28), goldenRow(29))
    }

    @Test
    fun `days 30 to 57 complete step 2 and reach the cap`() {
        assertEquals(Row(streak = 56, held = 5, step = 3, run = 0, daysToNext = null, frozen = setOf(8), best = 56), goldenRow(57))
        assertEquals(Moment(day(57), spent = emptyList(), earned = true), goldenMoment(57))
    }

    @Test
    fun `days 58 to 85 advance the step at the cap and bank nothing`() {
        assertEquals(Row(streak = 84, held = 5, step = 4, run = 0, daysToNext = null, frozen = setOf(8), best = 84), goldenRow(85))
        assertEquals(Moment(day(85), spent = emptyList(), earned = false), goldenMoment(85))
    }

    @Test
    fun `missed days 86 and 87 spend two freezes and two steps`() {
        val row = goldenRow(solvedThrough = 85, today = 88)
        assertEquals(Row(streak = 84, held = 3, step = 2, run = 0, daysToNext = 28, frozen = setOf(8, 86, 87), best = 84), row)
        assertEquals(Moment(day(88), spent = listOf(day(86), day(87)), earned = false), goldenMoment(88))
    }

    @Test
    fun `days 88 to 115 complete step 2 again`() {
        assertEquals(Row(streak = 112, held = 4, step = 3, run = 0, daysToNext = 28, frozen = setOf(8, 86, 87), best = 112), goldenRow(115))
    }

    @Test
    fun `missed days 116 to 119 spend every freeze and floor the step at 0`() {
        val row = goldenRow(solvedThrough = 115, today = 120)
        val frozen = setOf(8, 86, 87, 116, 117, 118, 119)
        assertEquals(Row(streak = 112, held = 0, step = 0, run = 0, daysToNext = 7, frozen = frozen, best = 112), row)
    }

    @Test
    fun `missed day 120 breaks the streak and unfreezes the gap`() {
        val row = goldenRow(solvedThrough = 115, today = 121)
        assertEquals(Row(streak = 0, held = 0, step = 0, run = 0, daysToNext = 7, frozen = setOf(8, 86, 87), best = 112), row)
    }

    @Test
    fun `day 121 starts a new streak with no freezes`() {
        assertEquals(Row(streak = 1, held = 0, step = 0, run = 1, daysToNext = 6, frozen = setOf(8, 86, 87), best = 112), goldenRow(121))
        assertEquals(Moment(day(121), spent = emptyList(), earned = false), goldenMoment(121))
    }

    @Test
    fun `today unsolved after a solved yesterday changes nothing`() {
        assertEquals(goldenRow(6), goldenRow(solvedThrough = 6, today = 7))
        assertEquals(goldenRow(57), goldenRow(solvedThrough = 57, today = 58))
    }

    @Test
    fun `the moment stays with the newest solve while later days are missed`() {
        assertEquals(Moment(day(115), spent = emptyList(), earned = true), goldenMoment(solvedThrough = 115, today = 120))
    }

    @Test
    fun `invoke current and best read the freeze-aware ledger`() {
        val streak = streakOn(day(9))
        val solved = (1..7).map(::day)
        assertEquals(7, streak(solved).current)
        assertEquals(7, streak(solved).best)
    }

    @Test
    fun `a gap of 2 holding 2 is bridged`() {
        val ledger = streakOn(day(4))(listOf(day(1), day(4)))
        val moment = Moment(day(4), spent = listOf(day(2), day(3)), earned = false)
        val freezes = StreakFreezes(held = 0, frozenDays = setOf(day(2), day(3)), daysToNext = 6, lastSolve = moment)
        assertEquals(StreakLedger(current = 2, best = 2, freezes = freezes), ledger)
    }

    @Test
    fun `a gap of 3 holding 2 breaks`() {
        val ledger = streakOn(day(5))(listOf(day(1), day(5)))
        val moment = Moment(day(5), spent = emptyList(), earned = false)
        val freezes = StreakFreezes(held = 0, frozenDays = emptySet(), daysToNext = 6, lastSolve = moment)
        assertEquals(StreakLedger(current = 1, best = 1, freezes = freezes), ledger)
    }

    @Test
    fun `a solve after today spends no freeze on today and leaves every number but best alone`() {
        val today = day(10)
        val streak = streakOn(today)

        val withFuture = streak(listOf(today.minusDays(1), today.plusDays(1)))
        val without = streak(listOf(today.minusDays(1)))

        assertEquals(1, withFuture.current)
        assertEquals(2, checkNotNull(withFuture.freezes).held)
        assertEquals(emptySet<LocalDate>(), withFuture.freezes.frozenDays)
        assertEquals(without.freezes, withFuture.freezes)
    }

    @Test
    fun `only solves after today leave an empty ledger but for best`() {
        val ledger = streakOn(day(10))(listOf(day(11), day(12)))
        val empty = StreakFreezes(held = 0, frozenDays = emptySet(), daysToNext = 7, lastSolve = null)
        assertEquals(StreakLedger(current = 0, best = 2, freezes = empty), ledger)
    }

    @Test
    fun `yesterday missed spends at the puzzle-day rollover, today open never does`() {
        val solved = listOf(day(1), day(2))

        val lastSecondOfDay3 = streakAt(Instant.parse("2026-01-03T23:59:59Z"))(solved)
        val firstSecondOfDay4 = streakAt(Instant.parse("2026-01-04T00:00:00Z"))(solved)

        val moment = Moment(day(2), spent = emptyList(), earned = false)
        val open = StreakFreezes(held = 2, frozenDays = emptySet(), daysToNext = 5, lastSolve = moment)
        val spent = StreakFreezes(held = 1, frozenDays = setOf(day(3)), daysToNext = 7, lastSolve = moment)
        assertEquals(StreakLedger(current = 2, best = 2, freezes = open), lastSecondOfDay3)
        assertEquals(StreakLedger(current = 2, best = 2, freezes = spent), firstSecondOfDay4)
    }

    @Test
    fun `flag off equals the strict streak on generated day sets and carries no freezes`() {
        val today = day(400)
        val streak = streakOn(today, FakeReleaseFlags())
        val random = Random(20261112)
        repeat(GENERATED_SETS) {
            val solved = generatedDays(random, today)
            val strict = StreakLedger(current = strictCurrent(solved, today), best = strictBest(solved), freezes = null)
            assertEquals("solved $solved", strict, streak(solved))
            assertEquals(strict.current, streak(solved).current)
            assertEquals(strict.best, streak(solved).best)
        }
    }

    @Test
    fun `the gap-jumping pass equals the day-by-day walk on generated day sets`() {
        val today = day(400)
        val streak = streakOn(today)
        val random = Random(20261112)
        repeat(GENERATED_SETS) {
            val solved = generatedDays(random, today)
            assertEquals("solved $solved", expectedReplay(solved, today), streak.replay(solved))
        }
    }

    @Test
    fun `any arrival order and duplicates give the same ledger`() {
        val today = day(400)
        val streak = streakOn(today)
        val random = Random(20261112)
        repeat(GENERATED_SETS) {
            val solved = generatedDays(random, today)
            val arrived = (solved + solved.take(5)).shuffled(random)
            assertEquals("solved $solved", streak(solved.sorted()), streak(arrived))
        }
    }

    @Test
    fun `a sparse 10,000-day history replays in one sweep`() {
        // Every 1,000 days: eight solved, one missed, seven solved. Each block ends 15 long on a
        // freshly earned freeze, then breaks in the gap to the next.
        val solved = (1..10_000 step 1_000).flatMap { first -> (first..first + 7) + (first + 9..first + 15) }.map(::day)
        val today = day(10_000)

        val replay = streakOn(today).replay(solved)

        val frozen = (9..10_000 step 1_000).mapTo(mutableSetOf(), ::day)
        val moment = Moment(day(9_016), spent = emptyList(), earned = true)
        val freezes = StreakFreezes(held = 0, frozenDays = frozen, daysToNext = 7, lastSolve = moment)
        assertEquals(ComputeStreak.Replay(StreakLedger(current = 0, best = 15, freezes = freezes), step = 0, run = 0), replay)
        assertEquals(dayByDayWalk(solved.toSet(), today), replay)
    }

    // A walk over every calendar day between these two solves would never finish.
    @Test(timeout = 10_000)
    fun `a gap is jumped, never walked day by day`() {
        val ledger = streakOn(day(1))(listOf(LocalDate.MIN, day(1)))
        assertEquals(1, ledger.current)
        assertEquals(0, ledger.freezes?.held)
    }

    /** Up to 400 days ending today, solved at a density from nearly never to nearly always; a few land after today. */
    private fun generatedDays(random: Random, today: LocalDate): List<LocalDate> {
        val density = DENSITIES.random(random)
        val past = (0L until random.nextLong(401)).filter { random.nextDouble() < density }.map(today::minusDays)
        val future = (1L..3L).filter { random.nextDouble() < FUTURE_DENSITY }.map(today::plusDays)
        return past + future
    }

    /** Every number but best reads only the days up to today; best walks them all, like the strict best. */
    private fun expectedReplay(solved: List<LocalDate>, today: LocalDate): ComputeStreak.Replay {
        val bounded = dayByDayWalk(solved.filter { it <= today }.toSet(), today)
        val best = dayByDayWalk(solved.toSet(), maxOf(today, solved.maxOrNull() ?: today)).ledger.best
        return bounded.copy(ledger = bounded.ledger.copy(best = best))
    }

    // The pre-freeze algorithm, copied as it was before the ledger: the flag-off oracle.
    private fun strictCurrent(solvedDays: Collection<LocalDate>, today: LocalDate): Int {
        val solved = solvedDays.toHashSet()
        var day = if (today in solved) today else today.minusDays(1)
        var streak = 0
        while (day in solved) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun strictBest(solvedDays: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in solvedDays.toSortedSet()) {
            run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** The rule as the player lives it, one calendar day at a time (A5): the flag-on oracle. */
    private fun dayByDayWalk(solved: Set<LocalDate>, today: LocalDate): ComputeStreak.Replay {
        var current = 0
        var best = 0
        var held = 0
        var step = 0
        var run = 0
        val frozen = mutableSetOf<LocalDate>()
        val spent = mutableListOf<LocalDate>()
        var lastSolve: Moment? = null
        var day = solved.minOrNull() ?: today
        while (day <= today) {
            when {
                day in solved -> {
                    if (lastSolve == null) held = 2
                    current++
                    run++
                    best = maxOf(best, current)
                    val stepDone = run == ladder(step)
                    val earned = stepDone && held < 5
                    if (stepDone) {
                        run = 0
                        step++
                    }
                    if (earned) held++
                    lastSolve = Moment(day, spent.toList(), earned)
                    spent.clear()
                }
                day == today -> Unit
                held > 0 -> {
                    held--
                    step = maxOf(0, step - 1)
                    run = 0
                    spent += day
                    frozen += day
                }
                else -> {
                    current = 0
                    step = 0
                    run = 0
                    frozen -= spent.toSet()
                    spent.clear()
                }
            }
            day = day.plusDays(1)
        }
        val daysToNext = if (held == 5) null else ladder(step) - run
        return ComputeStreak.Replay(StreakLedger(current, best, StreakFreezes(held, frozen, daysToNext, lastSolve)), step, run)
    }

    private fun ladder(step: Int): Int = if (step == 0) 7 else if (step == 1) 14 else 28

    private companion object {
        const val GENERATED_SETS = 500
        val DENSITIES = listOf(0.05, 0.5, 0.8, 0.9, 0.97, 0.995)
        const val FUTURE_DENSITY = 0.3
    }
}
