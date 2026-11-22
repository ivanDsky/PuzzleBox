package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSince
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The "Since" filter's line for this range (spec A2): "Since Nov 13", "Since Nov 1", "Since first solve". [since] is null
 * until the page has read its dates; each range then reads in words alone.
 */
internal fun StatsRange.sinceTitle(since: StatsSince?, locale: Locale = Locale.getDefault()): String = when (this) {
    StatsRange.Week -> since?.let { "Since ${it.first(this).format(DateTimeFormatter.ofPattern("MMM d", locale))}" } ?: "Last 7 days"
    StatsRange.Month -> since?.let { "Since ${it.first(this).format(DateTimeFormatter.ofPattern("MMM d", locale))}" } ?: "This month"
    StatsRange.All -> "Since first solve"
}

/** The sub-line under the range's row in the Since sheet: what the range is called, or the day of the first solve; null before the dates are read. */
internal fun StatsRange.sinceSubLine(since: StatsSince?, locale: Locale = Locale.getDefault()): String? = when {
    since == null -> null
    this == StatsRange.Week -> "Last 7 days"
    this == StatsRange.Month -> "Start of ${since.first(this).month.getDisplayName(TextStyle.FULL, locale)}"
    else -> since.firstSolve?.datedLabel(locale) ?: "No solves yet"
}
