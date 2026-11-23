package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The board read a post makes once the day's result is on LinkedIn, which serves a connections board only to a
 * player who has played the day. It goes through the day sync's budget, link checks and stores, but never asks
 * the day's cache window: whatever board the phone read before the post was read without the player's result.
 */
internal interface LinkedInBoardAfterPost {

    /** Reads the board of [game]'s [day] as [profileId] now, once, and says how that ended. */
    suspend fun read(game: GameId, day: LocalDate, profileId: String): Result

    enum class Result {
        /** The board was stored and holds the player's own row. */
        Published,

        /** The board was stored but LinkedIn does not list the player on it yet. */
        NotYet,

        /** Nothing was stored: offline, paused, unlinked, or the day has no board to address. */
        NotRead,
    }
}

/** A board that does not list the player yet is read once more after this long, and never a third time. */
internal const val BOARD_RETRY_MS = 20_000L

/**
 * Reads the board after a post that ended with the result on LinkedIn ([LinkedInPostState.Posted] or
 * [LinkedInPostState.AlreadyOnLinkedIn]) and nothing else. It runs in this scope, the app's: the result page may be
 * left before the answer, and the board must still be stored.
 */
internal fun CoroutineScope.readBoardAfterPost(
    reader: LinkedInBoardAfterPost,
    outcome: LinkedInPostState,
    game: GameId,
    day: LocalDate,
    profileId: String,
) {
    if (outcome != LinkedInPostState.Posted && outcome != LinkedInPostState.AlreadyOnLinkedIn) return
    launch {
        if (reader.read(game, day, profileId) == LinkedInBoardAfterPost.Result.NotYet) {
            delay(BOARD_RETRY_MS)
            reader.read(game, day, profileId)
        }
    }
}
