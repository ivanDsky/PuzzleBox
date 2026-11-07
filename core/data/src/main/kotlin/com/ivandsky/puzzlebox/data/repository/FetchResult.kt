package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc

/** The outcome of [SharedPuzzleRepository.fetch]. */
sealed interface FetchResult {
    data class Found(val doc: SharedPuzzleDoc) : FetchResult
    data object NotFound : FetchResult
    data object Offline : FetchResult
    data class UnsupportedVersion(val found: Int) : FetchResult
}
