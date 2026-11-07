package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/**
 * Opens a friend's puzzle by its share code (spec A13, A19). The board is re-verified on this
 * device, as the sender's solution never travels, and the verified puzzle is kept locally, so
 * a puzzle once opened still plays offline or after its author withdraws it.
 */
class LoadSharedPuzzle(
    private val registry: GameRegistry,
    private val shared: SharedPuzzleRepository,
    private val received: ReceivedPuzzleRepository,
    private val budget: () -> Budget,
    private val clock: () -> Instant,
) {
    /** A stored row wins (offline-safe, A13). Else fetch → registry lookup → SharedOpen.verify → upsert the row
     *  (postState Pending, the moment it opens, A19) → Opened. Pure; the caller runs it on DispatcherProvider.default. */
    suspend operator fun invoke(code: ShareCode): OpenOutcome {
        received.get(code)?.let { return OpenOutcome.Opened(it) }
        val doc = when (val fetched = shared.fetch(code)) {
            is SharedFetchResult.Found -> fetched.doc
            SharedFetchResult.NotFound -> return OpenOutcome.NotFound
            SharedFetchResult.Offline -> return OpenOutcome.Offline
            is SharedFetchResult.UnsupportedVersion -> return OpenOutcome.NeedsUpdate(fetched.found)
        }
        if (doc.unpublished) return OpenOutcome.NoLongerAvailable
        val gameId = GameId(doc.gameId)
        val plugin = registry[gameId] ?: return OpenOutcome.UnknownGame(doc.gameId)
        val editor = plugin.editor ?: return OpenOutcome.FailedCheck
        return when (val verified = SharedOpen.verify(editor, doc.shareCodecVersion, doc.payload, budget())) {
            is SharedOpenResult.Verified -> {
                val row = doc.toReceived(gameId, verified)
                received.upsert(row)
                OpenOutcome.Opened(row)
            }
            is SharedOpenResult.NeedsUpdate -> OpenOutcome.NeedsUpdate(verified.found)
            SharedOpenResult.NotShareable, SharedOpenResult.FailedCheck -> OpenOutcome.FailedCheck
        }
    }

    private fun SharedPuzzleDoc.toReceived(gameId: GameId, verified: SharedOpenResult.Verified) = ReceivedPuzzle(
        code = code,
        gameId = gameId,
        variant = verified.variant,
        sharedFormatVersion = verified.puzzle.formatVersion,
        puzzleBytes = verified.puzzle.payload,
        title = title,
        authorName = authorName,
        note = note,
        authorTimeMs = authorTimeMs,
        authorHints = authorHints,
        authorUid = authorUid,
        openedAt = clock(),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = verified.difficulty?.level,
    )
}
