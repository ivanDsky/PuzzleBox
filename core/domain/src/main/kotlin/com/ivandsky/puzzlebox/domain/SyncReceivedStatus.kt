package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * Brings an opened friend's puzzle up to date with its doc (spec A19): once the author takes it
 * down, the stored row is marked, so it still plays here but its board takes no new entries. A
 * tombstone is final, and a doc that can't be read says nothing, so only a found, unpublished
 * doc changes the row.
 */
class SyncReceivedStatus(private val shared: SharedPuzzleRepository, private val received: ReceivedPuzzleRepository) {
    /** Found with unpublished ⇒ received.markUnpublished(code); Offline / NotFound / UnsupportedVersion keep the row as is. */
    suspend operator fun invoke(code: ShareCode) {
        val fetched = shared.fetch(code)
        if (fetched is SharedFetchResult.Found && fetched.doc.unpublished) received.markUnpublished(code)
    }
}
