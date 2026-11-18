package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The shared-puzzle store (spec 16): publish, unpublish and fetch one document by its share code,
 * list the public ones and report one (ADR-0020 amendment).
 * Implementations pass `SharedPuzzleCommunityContract` (core:testing), which runs
 * `SharedPuzzleRepositoryContract` too.
 */
interface SharedPuzzleRepository {
    /** Create-if-absent with [SharedPuzzleDoc.code] == draft.reservedCode; an existing doc with authorUid == me counts as success. */
    suspend fun publish(doc: SharedPuzzleDoc): PublishResult

    /** Sets the doc's `unpublished` flag and nothing else. */
    suspend fun unpublish(code: ShareCode): UnpublishResult

    /** Reads the doc, unpublished or not; a newer [SharedPuzzleDoc.formatVersion] is [SharedFetchResult.UnsupportedVersion]. */
    suspend fun fetch(code: ShareCode): SharedFetchResult

    /**
     * The public, still-published puzzles that match [filter], created in the last
     * [CommunityFilter.windowMs] by the device clock or of any age when that is null: newest first,
     * at most [COMMUNITY_LIST_LIMIT], no paging. Asked of
     * the server, never a cache. A doc this version cannot read is left out.
     */
    suspend fun listPublic(filter: CommunityFilter): CommunityListResult

    /**
     * Records the signed-in player's report of [code], once per puzzle and player. Signed out it
     * is [ReportResult.SignedOut] and nothing is sent.
     */
    suspend fun report(code: ShareCode): ReportResult
}
