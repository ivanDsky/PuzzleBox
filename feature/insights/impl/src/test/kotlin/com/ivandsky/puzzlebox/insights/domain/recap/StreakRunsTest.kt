package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.StreakFreezes
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The runs a streak walk's answer splits into, and the part of one inside a period. */
class StreakRunsTest {

    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private fun freezes(vararg frozen: LocalDate) =
        StreakFreezes(held = 2, frozenDays = frozen.toSet(), daysToNext = null, lastSolve = null)

    @Test
    fun `no days is no run`() {
        assertEquals(emptyList<StreakRun>(), StreakRuns.of(emptyList(), freezes()).within(november))
    }

    @Test
    fun `consecutive days are one run, a gap splits it`() {
        val runs = StreakRuns.of(listOf(day(11, 1), day(11, 2), day(11, 3), day(11, 7), day(11, 8)), freezes())

        assertEquals(
            listOf(StreakRun(day(11, 1), day(11, 3), emptySet()), StreakRun(day(11, 7), day(11, 8), emptySet())),
            runs.within(november),
        )
    }

    @Test
    fun `a frozen day joins the runs either side of it and shows in the run`() {
        val runs = StreakRuns.of(listOf(day(11, 1), day(11, 3)), freezes(day(11, 2)))

        assertEquals(listOf(StreakRun(day(11, 1), day(11, 3), setOf(day(11, 2)))), runs.within(november))
        assertEquals(3, runs.within(november).single().days)
    }

    @Test
    fun `repeated and unordered days are one run`() {
        val runs = StreakRuns.of(listOf(day(11, 3), day(11, 1), day(11, 2), day(11, 2)), freezes())

        assertEquals(listOf(StreakRun(day(11, 1), day(11, 3), emptySet())), runs.within(november))
    }

    @Test
    fun `a run across the month's edge is cut to the month, frozen days cut with it`() {
        val runs = StreakRuns.of(listOf(day(10, 30), day(11, 1), day(11, 3)), freezes(day(10, 31), day(11, 2)))

        assertEquals(listOf(StreakRun(day(11, 1), day(11, 3), setOf(day(11, 2)))), runs.within(november))
        assertEquals(listOf(StreakRun(day(10, 30), day(10, 31), setOf(day(10, 31)))), runs.within(RecapPeriod.Month(YearMonth.of(2026, 10))))
    }

    @Test
    fun `a run that never reaches the period is left out`() {
        val run = StreakRun(day(10, 1), day(10, 5), emptySet())

        assertNull(run.within(november))
    }

    @Test
    fun `the whole year holds a run that crosses its months`() {
        val runs = StreakRuns.of(listOf(day(10, 31), day(11, 1)), freezes())

        assertEquals(listOf(StreakRun(day(10, 31), day(11, 1), emptySet())), runs.within(RecapPeriod.Year(2026)))
    }
}
