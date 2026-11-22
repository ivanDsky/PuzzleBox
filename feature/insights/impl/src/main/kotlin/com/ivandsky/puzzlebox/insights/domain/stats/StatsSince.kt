package com.ivandsky.puzzlebox.insights.domain.stats

import java.time.LocalDate

/**
 * What the "Since" filter dates its rows from: the puzzle day [today] and the day of the first counted solve in the
 * page's scope ([firstSolve], null with none). Each range's own first day is its [StatsRange.span] over these.
 */
internal data class StatsSince(val today: LocalDate, val firstSolve: LocalDate?) {

    /** The first day [range] covers. */
    fun first(range: StatsRange): LocalDate = range.span(today, firstSolve).first
}
