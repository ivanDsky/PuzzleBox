package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.label
import java.time.LocalDate
import java.util.Locale

/**
 * The title a recap carries on every card and on its share image (A22): the period's [label], except the current year
 * before Dec 31 of the puzzle day [today], which reads "<year> so far". A year opened on Dec 31 itself, a past year and
 * a year still ahead all read as the plain year.
 */
internal fun RecapPeriod.title(today: LocalDate, locale: Locale = Locale.getDefault()): String = when (this) {
    is RecapPeriod.Month -> label(locale)
    is RecapPeriod.Year -> if (today.year == year && today.isBefore(LocalDate.of(year, 12, 31))) "$id so far" else id
}

/** What a year built only from imported results says it covers (A22): "From 312 imported days". */
internal fun importedDaysLine(days: Int): String = "From $days imported ${if (days == 1) "day" else "days"}"
