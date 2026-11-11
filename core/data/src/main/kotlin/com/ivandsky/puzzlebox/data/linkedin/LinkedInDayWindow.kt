package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where a day stands relative to the game's today, which the sync and the day view must read the same way. */
internal class LinkedInDayWindow(private val calendar: GameCalendar) {

    /**
     * Whole days from [day] to the game's today, or null when the calendar has no today for [game].
     * Today is the game's cadence-zone date, never the device's, and the difference is taken between
     * calendar dates, so neither a 23- or 25-hour day nor the zone's midnight can shift it.
     */
    fun deltaOf(game: GameId, day: LocalDate): Int? =
        calendar.today(game)?.let { today -> ChronoUnit.DAYS.between(day, today).toInt() }

    /** LinkedIn serves a connections board only for today and the few days before it. */
    fun inBoardWindow(delta: Int?): Boolean =
        delta != null && delta in 0..LinkedInFetchPolicy.BOARD_WINDOW_DAYS
}
