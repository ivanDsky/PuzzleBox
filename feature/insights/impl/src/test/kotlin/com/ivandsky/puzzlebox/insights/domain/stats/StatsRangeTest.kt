package com.ivandsky.puzzlebox.insights.domain.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsRangeTest {

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun `week is the last seven puzzle days, today included`() {
        val week = StatsRange.Week.span(today, firstSolve = null)

        assertTrue(today in week)
        assertTrue(today.minusDays(6) in week)
        assertFalse(today.minusDays(7) in week)
        assertFalse(today.plusDays(1) in week)
    }

    @Test
    fun `month is the calendar month of today, both ends in and the neighbours out`() {
        val month = StatsRange.Month.span(today, firstSolve = null)

        assertTrue(LocalDate.of(2026, 11, 1) in month)
        assertTrue(LocalDate.of(2026, 11, 30) in month)
        assertFalse(LocalDate.of(2026, 10, 31) in month)
        assertFalse(LocalDate.of(2026, 12, 1) in month)
    }

    @Test
    fun `all runs from the first counted solve to today`() {
        val first = LocalDate.of(2025, 3, 4)
        val all = StatsRange.All.span(today, first)

        assertTrue(first in all)
        assertTrue(today in all)
        assertFalse(first.minusDays(1) in all)
        assertFalse(today.plusDays(1) in all)
    }

    @Test
    fun `all with no solve is today alone`() {
        val all = StatsRange.All.span(today, firstSolve = null)

        assertTrue(today in all)
        assertFalse(today.minusDays(1) in all)
    }

    @Test
    fun `an id reads back its range and a stale one reads as none`() {
        StatsRange.entries.forEach { assertEquals(it, StatsRange.parse(it.name)) }
        assertNull(StatsRange.parse("Quarter"))
        assertNull(StatsRange.parse(null))
    }
}
