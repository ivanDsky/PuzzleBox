package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.LocalDate

/**
 * What one puzzle [day]'s counted solves add up to, over every game in the facts' scope (A11: the all-games view sums
 * the dailies): [solved] solves taking [timeMs] of play time in all, [hintFree] of them with no hint. A stats series
 * buckets these by day or week, so it never needs the history again.
 */
internal data class DayTally(val day: LocalDate, val solved: Int, val timeMs: Long, val hintFree: Int)
