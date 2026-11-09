package com.ivandsky.puzzlebox.play

import java.time.LocalDate
import java.time.YearMonth

/** The calendar's callbacks: page a month, pick the day card's day, return to today. */
internal class CalendarActions(
    val onShowMonth: (YearMonth) -> Unit = {},
    val onPick: (LocalDate) -> Unit = {},
    val onBackToToday: () -> Unit = {},
)
