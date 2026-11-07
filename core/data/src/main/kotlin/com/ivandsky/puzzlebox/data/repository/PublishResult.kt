package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.ShareCode

/** The outcome of [SharedPuzzleRepository.publish]. */
sealed interface PublishResult {
    data class Published(val code: ShareCode) : PublishResult
    data object CodeTaken : PublishResult      // caller regenerates and re-reserves
    data object DailyCapReached : PublishResult
    data object Offline : PublishResult
    data object SignedOut : PublishResult
    data class Rejected(val message: String?) : PublishResult
}
