package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * One game day's LinkedIn data: the player's own result and their connections' board. The only door
 * to the account after connect, so the cache windows, the call budget and the pause latch hold for
 * every caller.
 */
interface LinkedInDays {
    /** One call per due item; one in-flight sync per (game, day, item); the first rejection pauses. */
    suspend fun sync(game: GameId, day: LocalDate): LinkedInSync
}
