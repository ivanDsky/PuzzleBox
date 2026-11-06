package com.ivandsky.puzzlebox.play

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** The grid always shows six Monday-first weeks, so a month change never moves the legend. */
internal const val GRID_DAYS = 42

/** One jump chip: "#1", a year or "Today", the [month] it shows, and whether it is the shown one. */
internal data class MonthJump(val label: String, val month: YearMonth, val selected: Boolean)

/**
 * The month calendar's arithmetic over an archive [range] (#1 .. today, 06-a §Month calendar):
 * ‹ › step a month inside it, the jumps reach #1's month, each year (keeping the shown
 * month-of-year, clamped) and today's month, and [grid] lays a month out Monday first.
 */
internal class ArchiveMonthNav(private val range: ClosedRange<LocalDate>) {

    val first: YearMonth = YearMonth.from(range.start)
    val last: YearMonth = YearMonth.from(range.endInclusive)

    fun clamp(month: YearMonth): YearMonth = month.coerceIn(first, last)

    fun hasPrevious(month: YearMonth): Boolean = month > first

    fun hasNext(month: YearMonth): Boolean = month < last

    fun previous(month: YearMonth): YearMonth = clamp(month.minusMonths(1))

    fun next(month: YearMonth): YearMonth = clamp(month.plusMonths(1))

    /** "#1", one chip per year from #1's to today's, "Today"; exactly one is [MonthJump.selected]. */
    fun jumps(shown: YearMonth): List<MonthJump> {
        val onFirst = shown == first
        val onLast = shown == last
        val years = (first.year..last.year).map { year ->
            MonthJump(year.toString(), clamp(YearMonth.of(year, shown.month)), !onFirst && !onLast && shown.year == year)
        }
        return listOf(MonthJump("#1", first, onFirst)) + years + MonthJump("Today", last, !onFirst && onLast)
    }

    /** Whether [day] is inside the archive (a disc), not before #1 or after today. */
    fun contains(day: LocalDate): Boolean = day in range

    /** [month]'s six weeks: from the Monday on or before its first day, [GRID_DAYS] days. */
    fun grid(month: YearMonth): List<LocalDate> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return List(GRID_DAYS) { start.plusDays(it.toLong()) }
    }
}
