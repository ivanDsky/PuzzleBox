package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSince
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Since …" label of each range and its sub-line (spec A2), cut from the range's own span. */
class StatsSinceLabelTest {

    private val today = LocalDate.of(2026, 11, 19)
    private val since = StatsSince(today, firstSolve = LocalDate.of(2025, 3, 30))

    @Test
    fun `week reads since the first of the last 7 days`() {
        assertEquals("Since Nov 13", StatsRange.Week.sinceTitle(since, Locale.US))
        assertEquals("Last 7 days", StatsRange.Week.sinceSubLine(since, Locale.US))
    }

    @Test
    fun `month reads since the 1st and names the month`() {
        assertEquals("Since Nov 1", StatsRange.Month.sinceTitle(since, Locale.US))
        assertEquals("Start of November", StatsRange.Month.sinceSubLine(since, Locale.US))
    }

    @Test
    fun `all reads since first solve and dates it`() {
        assertEquals("Since first solve", StatsRange.All.sinceTitle(since, Locale.US))
        assertEquals("Mar 30, 2025", StatsRange.All.sinceSubLine(since, Locale.US))
    }

    @Test
    fun `all with no solve yet has no date to give`() {
        val none = StatsSince(today, firstSolve = null)

        assertEquals("Since first solve", StatsRange.All.sinceTitle(none, Locale.US))
        assertEquals("No solves yet", StatsRange.All.sinceSubLine(none, Locale.US))
    }

    @Test
    fun `week crosses a month edge`() {
        val early = StatsSince(LocalDate.of(2026, 12, 3), firstSolve = null)

        assertEquals("Since Nov 27", StatsRange.Week.sinceTitle(early, Locale.US))
    }

    @Test
    fun `before the dates are read each range still has a title and no sub-line`() {
        assertEquals("Last 7 days", StatsRange.Week.sinceTitle(null, Locale.US))
        assertEquals("This month", StatsRange.Month.sinceTitle(null, Locale.US))
        assertEquals("Since first solve", StatsRange.All.sinceTitle(null, Locale.US))
        StatsRange.entries.forEach { assertNull(it.sinceSubLine(null, Locale.US)) }
    }
}
