package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The shared-puzzle store (spec 16): publish, unpublish and fetch one document by its share code.
 * Implementations pass `SharedPuzzleRepositoryContract` (core:testing).
 */
interface SharedPuzzleRepository {
    /** Create-if-absent with [SharedPuzzleDoc.code] == draft.reservedCode; an existing doc with authorUid == me counts as success. */
    suspend fun publish(doc: SharedPuzzleDoc): PublishResult

    /** Sets the doc's `unpublished` flag and nothing else. */
    suspend fun unpublish(code: ShareCode): UnpublishResult

    /** Reads the doc, unpublished or not; a newer [SharedPuzzleDoc.formatVersion] is [SharedFetchResult.UnsupportedVersion]. */
    suspend fun fetch(code: ShareCode): SharedFetchResult
}
