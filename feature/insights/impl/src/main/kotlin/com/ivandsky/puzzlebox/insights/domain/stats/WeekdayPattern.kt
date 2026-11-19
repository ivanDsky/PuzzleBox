package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.PeriodFacts
import java.time.DayOfWeek

/**
 * The average solve time of each weekday over the range, Monday first like the weeks of the trend lines: one [Day] per
 * weekday with a counted solve, so a weekday with no solve is not in the list (hidden). The weekday is the puzzle day's,
 * read off the facts' per-day tallies, never the instant a result was recorded or imported. With every game in the
 * scope it sums the dailies (A11).
 */
internal data class WeekdayPattern(val days: List<Day>) {

    /** The [averageMs] of the [solved] counted solves made on puzzle days that fall on [weekday]. */
    data class Day(val weekday: DayOfWeek, val averageMs: Long, val solved: Int)

    companion object {

        /** The pattern of [facts]; its days are empty when the range has no counted solve. */
        fun of(facts: PeriodFacts): WeekdayPattern {
            val byWeekday = facts.dayTallies.groupBy { it.day.dayOfWeek }
            return WeekdayPattern(
                DayOfWeek.entries.mapNotNull { weekday ->
                    val tallies = byWeekday[weekday].orEmpty()
                    val solved = tallies.sumOf { it.solved }
                    if (solved == 0) null else Day(weekday, tallies.sumOf { it.timeMs } / solved, solved)
                },
            )
        }
    }
}
