package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.gameui.GameBrand
import java.time.LocalDate

/**
 * What the calendar composables read, so History and Ranks drive the same calendar from their own state.
 * [shownDay] (null until the first emission) puts the week — or, [expanded], the month — in view,
 * between [earliestDay] and [today]. [solvedByDay] holds each day's numbered solves as brands,
 * [gamesPerDay] ring segments a day, [daysWithRows] the days with any row, [selectedDay] the one filled with ink.
 */
data class CalendarState(
    val shownDay: LocalDate? = null,
    val expanded: Boolean = false,
    val earliestDay: LocalDate? = null,
    val selectedDay: LocalDate? = null,
    val solvedByDay: Map<LocalDate, List<GameBrand>> = emptyMap(),
    val daysWithRows: Set<LocalDate> = emptySet(),
    val gamesPerDay: Int = 0,
    val today: LocalDate? = null,
) {
    /** Whether the calendar's [date] taps: a day with rows, never after today. */
    fun canSelect(date: LocalDate): Boolean = today != null && date <= today && date in daysWithRows
}
