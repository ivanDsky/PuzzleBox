package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc

/** The outcome of [SharedPuzzleRepository.fetch]. */
sealed interface SharedFetchResult {
    data class Found(val doc: SharedPuzzleDoc) : SharedFetchResult
    data object NotFound : SharedFetchResult
    data object Offline : SharedFetchResult
    data class UnsupportedVersion(val found: Int) : SharedFetchResult
}
