package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * In-memory [SharedPuzzleRepository] that passes the same contract as the Firestore adapter, plus
 * knobs for what a device cannot force on demand. Writes follow the server rules: only the
 * signed-in author creates or unpublishes a doc, and creates count against [DAILY_CAP].
 * Payloads are copied in and out, so callers can never mutate a stored doc.
 */
class FakeSharedPuzzleRepository : SharedPuzzleRepository {

    private val stored = LinkedHashMap<ShareCode, SharedPuzzleDoc>()

    /** Every stored doc by code, as last written. */
    val docs: Map<ShareCode, SharedPuzzleDoc> get() = synchronized(stored) { stored.mapValues { it.value.detached() } }

    /** The signed-in user; null makes publish [PublishResult.SignedOut] and unpublish [UnpublishResult.Rejected]. */
    @Volatile var signedInUid: String? = null

    /** False makes every call Offline and writes nothing. */
    @Volatile var online: Boolean = true

    /** The next publish writes its doc, then reports Offline, like a timeout after the commit. */
    @Volatile var commitThenOffline: Boolean = false

    /** Docs created today; a create that would exceed [DAILY_CAP] is [PublishResult.DailyCapReached]. */
    @Volatile var publishesToday: Int = 0

    override suspend fun publish(doc: SharedPuzzleDoc): PublishResult = synchronized(stored) {
        if (!online) return PublishResult.Offline
        val uid = signedInUid ?: return PublishResult.SignedOut
        if (doc.authorUid != uid) return PublishResult.Rejected("authorUid is not the signed-in user")
        val existing = stored[doc.code]
        when {
            existing != null && existing.authorUid == uid -> PublishResult.Published(doc.code)
            existing != null -> PublishResult.CodeTaken
            publishesToday >= DAILY_CAP -> PublishResult.DailyCapReached
            else -> {
                stored[doc.code] = doc.detached()
                publishesToday++
                if (commitThenOffline) {
                    commitThenOffline = false
                    PublishResult.Offline
                } else {
                    PublishResult.Published(doc.code)
                }
            }
        }
    }

    override suspend fun unpublish(code: ShareCode): UnpublishResult = synchronized(stored) {
        if (!online) return UnpublishResult.Offline
        val uid = signedInUid ?: return UnpublishResult.Rejected("signed out")
        val existing = stored[code] ?: return UnpublishResult.Rejected("no puzzle ${code.value}")
        if (existing.authorUid != uid) return UnpublishResult.Rejected("not the author of ${code.value}")
        stored[code] = existing.copy(unpublished = true)
        UnpublishResult.Done
    }

    override suspend fun fetch(code: ShareCode): SharedFetchResult = synchronized(stored) {
        if (!online) return SharedFetchResult.Offline
        val doc = stored[code] ?: return SharedFetchResult.NotFound
        if (doc.formatVersion > SharedPuzzleDoc.FORMAT_VERSION) return SharedFetchResult.UnsupportedVersion(doc.formatVersion)
        SharedFetchResult.Found(doc.detached())
    }

    private fun SharedPuzzleDoc.detached() = copy(payload = payload.copyOf())

    private companion object {
        const val DAILY_CAP = 20
    }
}
