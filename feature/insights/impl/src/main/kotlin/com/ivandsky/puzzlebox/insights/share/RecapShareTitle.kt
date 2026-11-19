package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.label
import java.time.LocalDate
import java.util.Locale

/**
 * The title a recap carries on its share image (A22): the period's [label], except the current year before Dec 31 of
 * the puzzle day [today], which reads "<year> so far". A year opened on Dec 31 itself, a past year and a year still
 * ahead all read as the plain year.
 */
internal fun RecapPeriod.shareTitle(today: LocalDate, locale: Locale = Locale.getDefault()): String = when (this) {
    is RecapPeriod.Month -> label(locale)
    is RecapPeriod.Year -> if (today.year == year && today.isBefore(LocalDate.of(year, 12, 31))) "$id so far" else id
}
