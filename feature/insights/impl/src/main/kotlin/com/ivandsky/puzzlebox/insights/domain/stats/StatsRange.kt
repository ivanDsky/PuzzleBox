package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.DaySpan
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.LocalDate
import java.time.YearMonth

/**
 * The range chip a stats page is read over (A10). Ids only travel: the entry [name] is what saved state keeps and
 * [parse] reads back.
 */
internal enum class StatsRange {
    /** The last 7 puzzle days, today included. */
    Week,

    /** The current calendar month, by puzzle day. */
    Month,

    /** Every day since the first counted solve. */
    All,
    ;

    /**
     * The days this range covers on the puzzle day [today]. [firstSolve] is the day of the first counted solve in the
     * page's scope; [All] with none is just today, so it has nothing to count.
     */
    fun span(today: LocalDate, firstSolve: LocalDate?): DaySpan = when (this) {
        Week -> DayRange(today.minusDays(WEEK_DAYS - 1L), today)
        Month -> RecapPeriod.Month(YearMonth.from(today))
        All -> DayRange(firstSolve ?: today, today)
    }

    companion object {
        private const val WEEK_DAYS = 7

        /** The range named by [id], or null for an id this build does not read (a stale saved state). */
        fun parse(id: String?): StatsRange? = entries.firstOrNull { it.name == id }
    }
}
