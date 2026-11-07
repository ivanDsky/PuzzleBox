package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * Unpublishes one of the author's puzzles: the shared doc's tombstone first, then the local row,
 * so the listing never shows a code as withdrawn while it still opens for new players.
 */
class UpdatePublishState(private val shared: SharedPuzzleRepository, private val published: PublishedPuzzleRepository) {
    /** Done ⇒ published.markUnpublished(code); Offline/Rejected leave the local row live. */
    suspend operator fun invoke(code: ShareCode): UnpublishResult {
        val result = shared.unpublish(code)
        if (result == UnpublishResult.Done) published.markUnpublished(code)
        return result
    }
}
