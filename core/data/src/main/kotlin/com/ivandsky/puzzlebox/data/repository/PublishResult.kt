package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.ShareCode

/**
 * Puzzles one author may create per UTC day; one more is [PublishResult.DailyCapReached].
 * backend/firebase/firestore.rules holds the same number for the server-side check.
 */
const val DAILY_PUBLISH_CAP = 20

/** The outcome of [SharedPuzzleRepository.publish]. */
sealed interface PublishResult {
    data class Published(val code: ShareCode) : PublishResult
    data object CodeTaken : PublishResult      // caller regenerates and re-reserves
    data object DailyCapReached : PublishResult
    /** The server refused the write and the device's UTC day is not the server's: a wrong clock. */
    data object ClockSkew : PublishResult
    data object Offline : PublishResult
    data object SignedOut : PublishResult
    data class Rejected(val message: String?) : PublishResult
}
