package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.game.editor.shareFormatVersion
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlin.random.Random

/**
 * Publish (spec A15, 16): the stored draft [draftId], open as [draft] with its live [verdict],
 * goes out as a shared document with the solution stripped, once it and its stored author name
 * and note pass their checks. Its code is reserved on the draft before the network call, so a
 * retry after a lost answer reuses it and finds its own document.
 *
 * The draft has one writer, the caller's: a code this use case picks (none reserved yet, or a
 * taken one replaced) is handed to `reserve`, which returns once the draft holds it, so a save
 * of an older row cannot put the old code back. The use case never writes the draft itself.
 */
class CreateSharedPuzzle(
    private val drafts: DraftRepository,
    private val shared: SharedPuzzleRepository,
    private val published: PublishedPuzzleRepository,
    private val identity: IdentityService,
    private val validate: ValidatePublish,
    private val random: Random,
    private val clock: () -> Instant,
) {
    private val validateDetails = ValidatePublishDetails()

    /** Blocks first; then reuse draft.reservedCode or reserve a new one (stored through [reserve] BEFORE the network call, A15);
     *  create-if-absent; CodeTaken ⇒ regenerate + re-reserve, at most 3 times, then Rejected; Published ⇒ published_puzzles row. */
    suspend operator fun invoke(
        draftId: String,
        draft: EditorDraft<*>,
        verdict: Verdict,
        reserve: suspend (code: ShareCode) -> Unit,
    ): PublishOutcome {
        val stored = drafts.get(draftId) ?: return PublishOutcome.Rejected("no draft $draftId")
        val blocks = validate(draft, verdict, stored.title, stored.testSolve) + validateDetails(stored.authorName, stored.note)
        if (blocks.isNotEmpty()) return PublishOutcome.Blocked(blocks)
        val payload = draft.encodeShare() ?: return PublishOutcome.Rejected("${draft.gameId.value} can't be shared")
        val shareVersion = checkNotNull(draft.shareFormatVersion)
        val uid = identity.current.value?.uid ?: return PublishOutcome.SignedOut
        var row = if (stored.reservedCode?.let(ShareCode::parse) != null) stored else stored.reserved(reserve)
        var regenerations = 0
        while (true) {
            val code = checkNotNull(ShareCode.parse(checkNotNull(row.reservedCode)))
            val doc = row.toDoc(code, uid, shareVersion, payload)
            when (val result = shared.publish(doc)) {
                is PublishResult.Published -> {
                    published.upsert(row.toListing(result.code, doc.createdAtMs))
                    return PublishOutcome.Published(result.code)
                }
                PublishResult.CodeTaken -> {
                    if (regenerations == MAX_REGENERATIONS) return PublishOutcome.Rejected("no free share code")
                    regenerations++
                    row = row.reserved(reserve)
                }
                PublishResult.DailyCapReached -> return PublishOutcome.DailyCapReached
                PublishResult.ClockSkew -> return PublishOutcome.ClockSkew
                PublishResult.Offline -> return PublishOutcome.Offline
                PublishResult.SignedOut -> return PublishOutcome.SignedOut
                is PublishResult.Rejected -> return PublishOutcome.Rejected(result.message)
            }
        }
    }

    /** Hands a fresh random code to [reserve]; [this] with it. The edit time stays: no board changed. */
    private suspend fun Draft.reserved(reserve: suspend (ShareCode) -> Unit): Draft {
        val code = ShareCode.random(random)
        reserve(code)
        return copy(reservedCode = code.value)
    }

    private fun Draft.toDoc(code: ShareCode, uid: String, shareVersion: Int, payload: ByteArray): SharedPuzzleDoc {
        val test = checkNotNull(testSolve)
        return SharedPuzzleDoc(
            code = code,
            formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
            gameId = gameId.value,
            variant = variant.value,
            shareCodecVersion = shareVersion,
            payload = payload,
            title = title.trim(),
            authorName = authorName.trim(),
            note = note.trim(),
            authorUid = uid,
            authorTimeMs = test.timeMs,
            authorHints = test.hints,
            createdAtMs = clock().toEpochMilli(),
            unpublished = false,
        )
    }

    private fun Draft.toListing(code: ShareCode, createdAtMs: Long): PublishedPuzzle {
        val test = checkNotNull(testSolve)
        return PublishedPuzzle(
            code = code,
            gameId = gameId,
            variant = variant,
            title = title.trim(),
            authorTimeMs = test.timeMs,
            authorHints = test.hints,
            createdAt = Instant.ofEpochMilli(createdAtMs),
            unpublished = false,
            solvesCount = null,
            solvesCountAt = null,
        )
    }

    private companion object {
        const val MAX_REGENERATIONS = 3
    }
}
