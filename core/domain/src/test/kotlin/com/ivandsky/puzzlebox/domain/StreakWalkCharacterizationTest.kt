package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the streak walk says about individual days, read only through [ComputeStreak.invoke]: the solved days plus
 * [StreakFreezes.frozenDays] are exactly the days the streak ran over, so every maximal run of consecutive such days is
 * one streak. A per-period streak figure is derived from that and defines no streak rule of its own, so these pin the
 * reading it relies on before anything builds on it.
 */
class StreakWalkCharacterizationTest {

    private val registry = TestGames.registry(listOf(GameId("zip")))

    // TestGames' puzzle zone is UTC, so noon UTC sits inside the puzzle day.
    private fun streakOn(today: LocalDate) =
        ComputeStreak(GetPuzzleDay(registry, Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)))

    private val today = LocalDate.of(2026, 11, 20)

    private fun day(offset: Long) = today.minusDays(offset)

    /** The maximal runs of consecutive days over [solved] and the ledger's frozen days, oldest first, as (first, last). */
    private fun runsOf(solved: Collection<LocalDate>, ledger: StreakLedger): List<Pair<LocalDate, LocalDate>> {
        val days = (solved + ledger.freezes.frozenDays).toSortedSet().toList()
        val runs = mutableListOf<Pair<LocalDate, LocalDate>>()
        for (d in days) {
            val last = runs.lastOrNull()
            if (last != null && last.second.plusDays(1) == d) runs[runs.lastIndex] = last.first to d else runs += d to d
        }
        return runs
    }

    private fun Pair<LocalDate, LocalDate>.length() = (ChronoUnit.DAYS.between(first, second) + 1).toInt()

    @Test
    fun `solved days with no gap are one run and no day is frozen`() {
        val solved = (0L..4L).map(::day)
        val ledger = streakOn(today)(solved)

        assertEquals(emptySet<LocalDate>(), ledger.freezes.frozenDays)
        assertEquals(listOf(day(4) to day(0)), runsOf(solved, ledger))
    }

    @Test
    fun `a bridged gap is frozen day by day and joins the two runs into one`() {
        val solved = listOf(day(7), day(6), day(3), day(2), day(1), day(0))
        val ledger = streakOn(today)(solved)

        assertEquals(setOf(day(5), day(4)), ledger.freezes.frozenDays)
        assertEquals(listOf(day(7) to day(0)), runsOf(solved, ledger))
        assertEquals(8, runsOf(solved, ledger).single().length())
    }

    @Test
    fun `a gap longer than the freezes held freezes nothing and splits the runs`() {
        val solved = listOf(day(9), day(8), day(4), day(3))
        val ledger = streakOn(today)(solved)

        assertEquals(emptySet<LocalDate>(), ledger.freezes.frozenDays)
        assertEquals(listOf(day(9) to day(8), day(4) to day(3)), runsOf(solved, ledger))
    }

    @Test
    fun `a day that arrives late turns a broken gap into a bridged one`() {
        val streak = streakOn(today)
        val before = listOf(day(8), day(4))
        val after = before + day(6)

        assertEquals(emptySet<LocalDate>(), streak(before).freezes.frozenDays)
        assertEquals(listOf(day(8) to day(8), day(4) to day(4)), runsOf(before, streak(before)))

        assertEquals(setOf(day(7), day(5)), streak(after).freezes.frozenDays)
        assertEquals(listOf(day(8) to day(4)), runsOf(after, streak(after)))
    }

    @Test
    fun `the days a streak is held through to the open today are frozen and run to yesterday`() {
        val solved = listOf(day(3), day(2))
        val ledger = streakOn(today)(solved)

        assertEquals(setOf(day(1)), ledger.freezes.frozenDays)
        assertEquals(listOf(day(3) to day(1)), runsOf(solved, ledger))
        assertEquals(ledger.current, runsOf(solved, ledger).single().length())
    }

    @Test
    fun `a streak that ended long ago leaves its run and no frozen day after it`() {
        val solved = listOf(day(30), day(29))
        val ledger = streakOn(today)(solved)

        assertEquals(0, ledger.current)
        assertEquals(emptySet<LocalDate>(), ledger.freezes.frozenDays)
        assertEquals(listOf(day(30) to day(29)), runsOf(solved, ledger))
    }

    @Test
    fun `the longest run is the best streak on generated histories`() {
        val streak = streakOn(today)
        val random = Random(20261119)
        repeat(GENERATED_SETS) {
            val solved = generatedDays(random)
            val ledger = streak(solved)
            val runs = runsOf(solved, ledger)
            assertEquals("solved $solved", ledger.best, runs.maxOfOrNull { it.length() } ?: 0)
        }
    }

    @Test
    fun `the run that reaches today or yesterday is the current streak on generated histories`() {
        val streak = streakOn(today)
        val random = Random(20261119)
        repeat(GENERATED_SETS) {
            val solved = generatedDays(random)
            val ledger = streak(solved)
            val alive = runsOf(solved, ledger).lastOrNull { it.second >= day(1) }
            assertEquals("solved $solved", ledger.current, alive?.length() ?: 0)
        }
    }

    @Test
    fun `every frozen day lies between two solved days or after the last solve before today`() {
        val streak = streakOn(today)
        val random = Random(20261119)
        repeat(GENERATED_SETS) {
            val solved = generatedDays(random)
            val frozen = streak(solved).freezes.frozenDays
            for (d in frozen) {
                assertTrue("$d is solved", d !in solved)
                assertTrue("$d is after today", d < today)
                assertTrue("$d has no solve before it", solved.any { it < d })
            }
        }
    }

    /** Up to 400 days ending today, solved at a density from nearly never to nearly always; none after today. */
    private fun generatedDays(random: Random): List<LocalDate> {
        val density = DENSITIES.random(random)
        return (0L until random.nextLong(401)).filter { random.nextDouble() < density }.map(::day)
    }

    private companion object {
        const val GENERATED_SETS = 500
        val DENSITIES = listOf(0.05, 0.5, 0.8, 0.9, 0.97, 0.995)
    }
}
