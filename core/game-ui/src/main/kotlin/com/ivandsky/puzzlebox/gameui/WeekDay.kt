package com.ivandsky.puzzlebox.gameui

import java.time.LocalDate

/**
 * One column of a [WeekStrip] (spec A18): the puzzle [date], the brands of the games
 * solved on it (one [DayRing] segment each), and whether it is today. A pure value:
 * callers build the week from their own data, so no domain logic lives in the UI.
 */
data class WeekDay(val date: LocalDate, val solved: List<GameBrand>, val isToday: Boolean)
