package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * A7's submit window, said once: a daily time can post only while [day] is the game's
 * today or yesterday ([GameCalendar.today] decides; a game with no day never opens the
 * window). The submit watcher gates on it, and the play panel asks it before claiming a
 * solve is posting (RG10 — an older day can never post, so it is never "posting").
 */
fun GameCalendar.inSubmitWindow(gameId: GameId, day: LocalDate): Boolean {
    val today = today(gameId) ?: return false
    return day == today || day == today.minusDays(1)
}
