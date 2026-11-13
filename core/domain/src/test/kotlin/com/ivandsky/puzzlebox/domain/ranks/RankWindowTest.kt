package com.ivandsky.puzzlebox.domain.ranks

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Windows are puzzle-day spans: Mon–Sun weeks, calendar months, clipped at today (A13, 03 handoff §5). */
class RankWindowTest {

    private fun day(iso: String): LocalDate = LocalDate.parse(iso)

    private fun span(from: String, to: String): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var cursor = day(from)
        while (!cursor.isAfter(day(to))) {
            days += cursor
            cursor = cursor.plusDays(1)
        }
        return days
    }

    @Test
    fun `day window is its anchor while the anchor is not in the future`() {
        assertEquals(listOf(day("2026-11-12")), RankWindow.Day(day("2026-11-12")).days(day("2026-11-13")))
        assertEquals(listOf(day("2026-11-13")), RankWindow.Day(day("2026-11-13")).days(day("2026-11-13")))
    }

    @Test
    fun `day window in the future has no days`() {
        assertEquals(emptyList<LocalDate>(), RankWindow.Day(day("2026-11-14")).days(day("2026-11-13")))
    }

    @Test
    fun `week runs Monday to Sunday around any anchor in it`() {
        val week = span("2026-11-09", "2026-11-15")
        for (anchor in listOf("2026-11-09", "2026-11-12", "2026-11-15")) {
            assertEquals("anchor $anchor", week, RankWindow.Week(day(anchor)).days(day("2026-11-30")))
        }
    }

    @Test
    fun `week containing a Sunday starts on the Monday six days before`() {
        assertEquals(span("2026-10-26", "2026-11-01"), RankWindow.Week(day("2026-11-01")).days(day("2026-11-30")))
    }

    @Test
    fun `current week is clipped at today`() {
        assertEquals(span("2026-11-09", "2026-11-12"), RankWindow.Week(day("2026-11-12")).days(day("2026-11-12")))
    }

    @Test
    fun `week after today has no days`() {
        assertEquals(emptyList<LocalDate>(), RankWindow.Week(day("2026-11-16")).days(day("2026-11-13")))
    }

    @Test
    fun `month covers the anchor's calendar month`() {
        assertEquals(span("2026-11-01", "2026-11-30"), RankWindow.Month(day("2026-11-12")).days(day("2026-12-01")))
        assertEquals(span("2026-10-01", "2026-10-31"), RankWindow.Month(day("2026-10-31")).days(day("2026-11-13")))
    }

    @Test
    fun `leap February has 29 days`() {
        assertEquals(29, RankWindow.Month(day("2028-02-10")).days(day("2028-03-01")).size)
    }

    @Test
    fun `current month is clipped at today`() {
        assertEquals(span("2026-11-01", "2026-11-13"), RankWindow.Month(day("2026-11-12")).days(day("2026-11-13")))
    }

    @Test
    fun `month after today has no days`() {
        assertEquals(emptyList<LocalDate>(), RankWindow.Month(day("2026-12-03")).days(day("2026-11-13")))
    }

    @Test
    fun `case 10 anchors on puzzle-day labels`() {
        // Tango solves on 10-31 (submitted 11-01, +1), 11-08, 11-09, 11-15 and 11-16; anchor 11-12.
        val today = day("2026-11-30")
        val week = RankWindow.Week(day("2026-11-12")).days(today)
        val november = RankWindow.Month(day("2026-11-12")).days(today)
        val october = RankWindow.Month(day("2026-10-31")).days(today)

        assertEquals(span("2026-11-09", "2026-11-15"), week)
        assertEquals(span("2026-11-01", "2026-11-30"), november)
        assertEquals(span("2026-10-01", "2026-10-31"), october)
        assertFalse(day("2026-10-31") in november)
        assertFalse(day("2026-11-16") in week)
    }
}
