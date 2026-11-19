package com.ivandsky.puzzlebox.insights.recap

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A puzzle day as a card writes it: "Nov 12". The year is the period's, so it is left off. */
internal fun LocalDate.shortLabel(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("MMM d", locale))
