package com.ivandsky.puzzlebox.domain

import java.time.DayOfWeek

/**
 * The spread of this game's solves on [day] only, this solve's weekday. [fastest]: its earlier
 * average is the lowest of the weekdays that have at least [WEEKDAY_MIN_EARLIER] earlier
 * solves, with another weekday to beat.
 */
data class WeekdaySpread(val day: DayOfWeek, val spread: TimeSpread, val fastest: Boolean)
