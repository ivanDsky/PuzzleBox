package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_LIST_LIMIT
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.DAILY_PUBLISH_CAP
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * In-memory [SharedPuzzleRepository] that passes the same contract as the Firestore adapter, plus
 * knobs for what a device cannot force on demand. Writes follow the server rules: only the
 * signed-in author creates or unpublishes a doc, creates count against [DAILY_PUBLISH_CAP], and a
 * player reports another author's puzzle once.
 * Payloads are copied in and out, so callers can never mutate a stored doc.
 */
class FakeSharedPuzzleRepository : SharedPuzzleRepository {

    private val stored = LinkedHashMap<ShareCode, SharedPuzzleDoc>()
    private val reports = HashSet<Pair<ShareCode, String>>()

    /** Every stored doc by code, as last written. */
    val docs: Map<ShareCode, SharedPuzzleDoc> get() = synchronized(stored) { stored.mapValues { it.value.detached() } }

    /** The signed-in user; null makes publish [PublishResult.SignedOut] and unpublish [UnpublishResult.Rejected]. */
    @Volatile var signedInUid: String? = null

    /** False makes every call Offline and writes nothing. */
    @Volatile var online: Boolean = true

    /** The next publish writes its doc, then reports Offline, like a timeout after the commit. */
    @Volatile var commitThenOffline: Boolean = false

    /** Docs created today; a create past [DAILY_PUBLISH_CAP] is [PublishResult.DailyCapReached]. */
    @Volatile var publishesToday: Int = 0

    /** The device clock the community list measures its window from. */
    @Volatile var clock: () -> Long = System::currentTimeMillis

    /** True makes [listPublic] Refused, as the server answers a query its rules or indexes do not allow. */
    @Volatile var listRefused: Boolean = false

    override suspend fun publish(doc: SharedPuzzleDoc): PublishResult = synchronized(stored) {
        if (!online) return PublishResult.Offline
        val uid = signedInUid ?: return PublishResult.SignedOut
        if (doc.authorUid != uid) return PublishResult.Rejected("authorUid is not the signed-in user")
        val existing = stored[doc.code]
        when {
            existing != null && existing.authorUid == uid && !existing.unpublished -> {
                if (existing.sameContentAs(doc)) {
                    PublishResult.Published(doc.code)
                } else {
                    // The author's lost-reply doc is retired, so it is not left online unreachable.
                    stored[doc.code] = existing.copy(unpublished = true)
                    PublishResult.CodeTaken
                }
            }
            existing != null -> PublishResult.CodeTaken
            publishesToday >= DAILY_PUBLISH_CAP -> PublishResult.DailyCapReached
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

    override suspend fun listPublic(filter: CommunityFilter): CommunityListResult = synchronized(stored) {
        if (!online) return CommunityListResult.Offline
        if (listRefused) return CommunityListResult.Refused
        val oldest = filter.windowMs?.let { clock() - it } ?: Long.MIN_VALUE
        val listed = stored.values.filter { doc ->
            doc.visibility == SharedPuzzleDoc.Visibility.Public &&
                !doc.unpublished &&
                doc.formatVersion <= SharedPuzzleDoc.FORMAT_VERSION &&
                doc.createdAtMs >= oldest &&
                (filter.gameId == null || doc.gameId == filter.gameId) &&
                (filter.difficulties.isEmpty() || doc.difficulty in filter.difficulties)
        }
        CommunityListResult.Listed(
            listed.sortedWith(compareByDescending<SharedPuzzleDoc> { it.createdAtMs }.thenByDescending { it.code.value })
                .take(COMMUNITY_LIST_LIMIT)
                .map { it.detached() },
        )
    }

    override suspend fun report(code: ShareCode): ReportResult = synchronized(stored) {
        val uid = signedInUid ?: return ReportResult.SignedOut
        if (!online) return ReportResult.Offline
        val puzzle = stored[code]
        // The server refuses all three the same way, so the fake cannot tell them apart either.
        val refused = puzzle == null || puzzle.authorUid == uid || !reports.add(code to uid)
        if (refused) ReportResult.AlreadyReported else ReportResult.Reported
    }

    /** What a friend sees or plays; `createdAtMs` and `unpublished` are judged apart. */
    private fun SharedPuzzleDoc.sameContentAs(other: SharedPuzzleDoc) =
        copy(createdAtMs = 0, unpublished = false).let { mine ->
            val theirs = other.copy(createdAtMs = 0, unpublished = false)
            mine.copy(payload = byteArrayOf()) == theirs.copy(payload = byteArrayOf()) &&
                payload.contentEquals(other.payload)
        }

    private fun SharedPuzzleDoc.detached() = copy(payload = payload.copyOf())
}
