package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapPeriodTest {

    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))

    @Test
    fun `a month's id is year and month`() {
        assertEquals("2026-11", november.id)
        assertEquals("2026-01", RecapPeriod.Month(YearMonth.of(2026, 1)).id)
    }

    @Test
    fun `a year's id is the year`() {
        assertEquals("2025", RecapPeriod.Year(2025).id)
    }

    @Test
    fun `an id parses back to its period`() {
        assertEquals(november, RecapPeriod.parse(november.id))
        assertEquals(RecapPeriod.Year(2025), RecapPeriod.parse("2025"))
    }

    @Test
    fun `an id this build cannot read parses to null`() {
        listOf("", "2026-13", "2026-00", "26-11", "2026-1", "november", "2026-11-05", "20255").forEach {
            assertNull(it, RecapPeriod.parse(it))
        }
    }

    @Test
    fun `a month holds the puzzle days of its calendar month`() {
        assertTrue(LocalDate.of(2026, 11, 1) in november)
        assertTrue(LocalDate.of(2026, 11, 30) in november)
        assertFalse(LocalDate.of(2026, 10, 31) in november)
        assertFalse(LocalDate.of(2026, 12, 1) in november)
        assertFalse(LocalDate.of(2025, 11, 15) in november)
    }

    @Test
    fun `a year holds the puzzle days of its calendar year`() {
        assertTrue(LocalDate.of(2025, 1, 1) in RecapPeriod.Year(2025))
        assertTrue(LocalDate.of(2025, 12, 31) in RecapPeriod.Year(2025))
        assertFalse(LocalDate.of(2026, 1, 1) in RecapPeriod.Year(2025))
    }

    @Test
    fun `a month reads the Ranks month window and a year has none`() {
        assertEquals(RankWindow.Month(LocalDate.of(2026, 11, 1)), november.rankWindow)
        assertNull(RecapPeriod.Year(2025).rankWindow)
    }
}
