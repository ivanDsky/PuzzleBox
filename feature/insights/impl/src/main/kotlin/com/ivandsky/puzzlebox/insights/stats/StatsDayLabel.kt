package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A puzzle day as a record writes it: "Nov 12, 2026". A range can reach back years, so the year stays. */
internal fun LocalDate.datedLabel(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))

/** A puzzle day short for a chart's time axis: "Nov 12"; with [withYear] (a range over more than one year) the dated form. */
internal fun LocalDate.axisLabel(withYear: Boolean, locale: Locale = Locale.getDefault()): String =
    if (withYear) datedLabel(locale) else format(DateTimeFormatter.ofPattern("MMM d", locale))

/** The bucket of a stats line that starts on this puzzle day, in words: the day itself, or "Week of Nov 9, 2026". */
internal fun LocalDate.bucketLabel(granularity: StatsGranularity, locale: Locale = Locale.getDefault()): String =
    when (granularity) {
        StatsGranularity.Day -> datedLabel(locale)
        StatsGranularity.Week -> "Week of ${datedLabel(locale)}"
    }
