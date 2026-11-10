package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.model.ShareCode

/** What [CreateMatch] answers; it never throws. [DailyCapReached] is the server's quota, never shown as offline. */
sealed interface CreateOutcome {
    data class Created(val code: ShareCode, val legs: List<MatchLeg>) : CreateOutcome
    data object SignedOut : CreateOutcome
    data object Offline : CreateOutcome
    data object DailyCapReached : CreateOutcome
    data object NoPuzzles : CreateOutcome
    data class Failed(val message: String?) : CreateOutcome
}
