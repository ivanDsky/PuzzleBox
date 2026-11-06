package com.ivandsky.puzzlebox.play

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The month calendar's arithmetic (06-a §Month calendar): bounds, jumps and the grid. */
class ArchiveMonthNavTest {

    private val first = LocalDate.parse("2024-03-04")
    private val today = LocalDate.parse("2026-11-06")
    private val nav = ArchiveMonthNav(first..today)

    @Test
    fun `steps stay inside #1's month and today's`() {
        assertFalse(nav.hasPrevious(YearMonth.of(2024, 3)))
        assertTrue(nav.hasNext(YearMonth.of(2024, 3)))
        assertFalse(nav.hasNext(YearMonth.of(2026, 11)))
        assertEquals(YearMonth.of(2024, 3), nav.previous(YearMonth.of(2024, 3)))
        assertEquals(YearMonth.of(2026, 11), nav.next(YearMonth.of(2026, 11)))
        assertEquals(YearMonth.of(2025, 1), nav.next(YearMonth.of(2024, 12)))
        assertEquals(YearMonth.of(2024, 3), nav.clamp(YearMonth.of(2023, 7)))
        assertEquals(YearMonth.of(2026, 11), nav.clamp(YearMonth.of(2027, 1)))
    }

    @Test
    fun `jumps are #1, each year and today, with exactly one selected`() {
        val shown = YearMonth.of(2025, 6)
        val jumps = nav.jumps(shown)
        assertEquals(listOf("#1", "2024", "2025", "2026", "Today"), jumps.map { it.label })
        assertEquals(listOf("2025"), jumps.filter { it.selected }.map { it.label })
        assertEquals(YearMonth.of(2024, 6), jumps[1].month)
        assertEquals(YearMonth.of(2026, 6), jumps[3].month)
        assertEquals(YearMonth.of(2024, 3), jumps.first().month)
        assertEquals(YearMonth.of(2026, 11), jumps.last().month)
    }

    @Test
    fun `a year jump clamps the month-of-year into the archive`() {
        val jumps = nav.jumps(YearMonth.of(2025, 1))
        assertEquals("before #1 clamps to #1's month", YearMonth.of(2024, 3), jumps[1].month)
        assertEquals(YearMonth.of(2026, 1), jumps[3].month)
        assertEquals(YearMonth.of(2026, 11), nav.jumps(YearMonth.of(2025, 12))[3].month)
    }

    @Test
    fun `#1 and Today win the selection on their months`() {
        assertEquals(listOf("#1"), nav.jumps(YearMonth.of(2024, 3)).filter { it.selected }.map { it.label })
        assertEquals(listOf("Today"), nav.jumps(YearMonth.of(2026, 11)).filter { it.selected }.map { it.label })
    }

    @Test
    fun `the grid is six weeks from the Monday on or before the 1st`() {
        val grid = nav.grid(YearMonth.of(2026, 11))
        assertEquals(GRID_DAYS, grid.size)
        assertEquals(DayOfWeek.MONDAY, grid.first().dayOfWeek)
        assertEquals(LocalDate.parse("2026-10-26"), grid.first())
        assertTrue(LocalDate.parse("2026-11-30") in grid)
        val mondayFirst = nav.grid(YearMonth.of(2025, 9))
        assertEquals("a month starting on Monday starts the grid", LocalDate.parse("2025-09-01"), mondayFirst.first())
    }

    @Test
    fun `only #1 through today are discs`() {
        assertFalse(nav.contains(first.minusDays(1)))
        assertTrue(nav.contains(first))
        assertTrue(nav.contains(today))
        assertFalse(nav.contains(today.plusDays(1)))
    }
}
