package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Posting one game day's result to LinkedIn. The only door to the write, so the posting consent, the check
 * before every send, the one post per day and the pause latch hold for every caller.
 */
interface LinkedInPosts {
    /**
     * What the day's Post button says, a running post and its outcome included, emitted again when it changes.
     * Reads local state only and never asks LinkedIn. A day that is not the game's today has no button. The
     * game's midnight emits nothing by itself, so a button held open across it may stay until a tap is refused.
     */
    fun observe(game: GameId, day: LocalDate): Flow<LinkedInPostState>

    /**
     * Posts the day's result unless LinkedIn already holds one, and returns how that ended. LinkedIn is asked
     * first on every call. Callers of one day share one run, which goes on after its caller is cancelled, so
     * a day is sent at most once however often it is asked. Sends nothing without posting consent.
     */
    suspend fun submit(game: GameId, day: LocalDate): LinkedInPostState
}
