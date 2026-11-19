package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.StreakFreezes
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One streak as it ran day by day: every puzzle day from [start] to [end] was solved or held by a freeze, and the days
 * on either side were neither. [frozen] are the days in it a freeze held ([StreakFreezes.frozenDays]); a frozen day is
 * a streak day, so [days] counts it.
 */
internal data class StreakRun(val start: LocalDate, val end: LocalDate, val frozen: Set<LocalDate>) {

    /** The streak's length in days, frozen days included: the number the game page's best streak counts. */
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    /** The part of this streak inside [span], or null when it never reaches it: a streak across a month edge counts only its own side. */
    fun within(span: DaySpan): StreakRun? {
        val inside = generateSequence(start) { it.plusDays(1).takeIf { next -> next <= end } }.filter { it in span }.toList()
        if (inside.isEmpty()) return null
        return StreakRun(inside.first(), inside.last(), frozen.filterTo(mutableSetOf()) { it in span })
    }
}
