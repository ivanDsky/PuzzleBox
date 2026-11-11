package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * Syncs a day once it has stayed on screen for [debounceMs]. The wait is what keeps scrubbing
 * through days from asking LinkedIn anything: a day shown for less than that makes no call.
 *
 * It runs in the caller's coroutine and starts none of its own, so cancelling the caller (the
 * day left the screen) cancels the wait or the running sync alike, and nothing retries. Which
 * day's coroutine is alive is the caller's decision; two callers are not debounced against
 * each other.
 *
 * Whether the day needs a call at all is the sync's answer, not checked here, so there is one
 * source for "linked".
 */
class SyncLinkedInDay(private val days: LinkedInDays, private val debounceMs: Long = 500) {

    suspend operator fun invoke(game: GameId, day: LocalDate): LinkedInSync {
        delay(debounceMs)
        return days.sync(game, day)
    }
}
