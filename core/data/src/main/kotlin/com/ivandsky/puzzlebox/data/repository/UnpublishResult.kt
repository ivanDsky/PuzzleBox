package com.ivandsky.puzzlebox.data.repository

/** The outcome of [SharedPuzzleRepository.unpublish]. */
sealed interface UnpublishResult {
    data object Done : UnpublishResult
    data object Offline : UnpublishResult
    data class Rejected(val message: String?) : UnpublishResult
}
