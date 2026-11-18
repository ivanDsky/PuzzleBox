package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/** What [CreateMatch] answers; it never throws. [DailyCapReached] is the server's quota, never shown as offline. */
sealed interface CreateOutcome {
    data class Created(val code: ShareCode, val legs: List<MatchLeg>) : CreateOutcome
    data object SignedOut : CreateOutcome
    data object Offline : CreateOutcome
    data object DailyCapReached : CreateOutcome
    data object NoPuzzles : CreateOutcome
    /** The lineup entry at [position] (from 1) is an "Any" of [gameId] with no puzzle left to draw. */
    data class EntryUndrawable(val position: Int, val gameId: GameId) : CreateOutcome
    /** The exact puzzle at [position] (from 1) was taken down or can't be read, so the duel would have fewer legs than the lineup. */
    data class EntryUnavailable(val position: Int) : CreateOutcome
    data class Failed(val message: String?) : CreateOutcome
}
