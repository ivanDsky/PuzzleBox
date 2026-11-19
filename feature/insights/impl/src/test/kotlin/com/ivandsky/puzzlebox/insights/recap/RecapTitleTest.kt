package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** A22: a month is named by its month, the year under way is "so far", and a finished year is just the year. */
class RecapTitleTest {

    private val today = LocalDate.of(2026, 11, 19)

    private fun year(year: Int, on: LocalDate = today) = RecapPeriod.Year(year).title(on, Locale.US)

    @Test
    fun `a month reads as its name and year`() {
        assertEquals("November 2026", RecapPeriod.Month(YearMonth.of(2026, 11)).title(today, Locale.US))
        assertEquals("March 2025", RecapPeriod.Month(YearMonth.of(2025, 3)).title(today, Locale.US))
    }

    @Test
    fun `the current year before its last day reads so far`() {
        assertEquals("2026 so far", year(2026))
        assertEquals("2026 so far", year(2026, LocalDate.of(2026, 1, 1)))
        assertEquals("2026 so far", year(2026, LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun `a finished year reads as the plain year`() {
        assertEquals("2026", year(2026, LocalDate.of(2026, 12, 31)))
        assertEquals("2025", year(2025))
        assertEquals("2025", year(2025, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `a year that has not begun reads as the plain year`() {
        assertEquals("2027", year(2027))
    }

    @Test
    fun `an imports-only year says how many days it covers`() {
        assertEquals("From 312 imported days", importedDaysLine(312))
        assertEquals("From 1 imported day", importedDaysLine(1))
    }
}
