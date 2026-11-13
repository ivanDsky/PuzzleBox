package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * One game day's LinkedIn data: the player's own result and their connections' board. The only door
 * to the account after connect, so the cache windows, the call budget and the pause latch hold for
 * every caller.
 */
interface LinkedInDays {
    /** One call per due item; one in-flight sync per (game, day, item); the first rejection pauses. */
    suspend fun sync(game: GameId, day: LocalDate): LinkedInSync

    /**
     * Whether [sync] can read this day from LinkedIn at all, whatever the link and the cache say: the game is a
     * LinkedIn one, the day is not in the future and it has a puzzle number. False means the day is settled
     * on what the phone holds and no sync will change it. Reads local state only.
     */
    fun willRead(game: GameId, day: LocalDate): Boolean

    /**
     * Everything this phone knows about one game day from LinkedIn, emitted again when any of it
     * changes. Reads local state only and never asks LinkedIn, so collecting it is always safe.
     */
    fun observe(game: GameId, day: LocalDate): Flow<LinkedInDayData>
}
