package com.ivandsky.puzzlebox.insights

import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The period as the player reads it: "November 2026" for a month, "2025" for a year. */
internal fun RecapPeriod.label(locale: Locale = Locale.getDefault()): String = when (this) {
    is RecapPeriod.Month -> month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    is RecapPeriod.Year -> id
}
