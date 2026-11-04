package com.ivandsky.puzzlebox.data.calendar

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The one answer to "what day is today for game X" (A9, A23): each game's day follows its
 * own cadence zone, so the daily fetch and the hub cues roll over together at that game's
 * midnight (SC8). Tests pin it with `FakeGameCalendar`.
 */
interface GameCalendar {
    /** The game's current day, or null when the registry does not know [game]. */
    fun today(game: GameId): LocalDate?

    /** Emits the game's current day now, then again at each cadence-zone midnight.
     *  Empty for an unregistered game. */
    fun observeToday(game: GameId): Flow<LocalDate>
}
