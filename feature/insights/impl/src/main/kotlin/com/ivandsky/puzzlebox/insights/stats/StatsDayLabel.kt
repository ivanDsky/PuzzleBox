package com.ivandsky.puzzlebox.insights.stats

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A puzzle day as a record writes it: "Nov 12, 2026". A range can reach back years, so the year stays. */
internal fun LocalDate.datedLabel(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
