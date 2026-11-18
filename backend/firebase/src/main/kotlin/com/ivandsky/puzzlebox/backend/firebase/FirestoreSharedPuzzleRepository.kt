package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_LIST_LIMIT
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore implementation of the shared-puzzle seam over `puzzles/{code}`, its reports and
 * `authors/{uid}` (ADR-0020). Publish is create-if-absent, so a retry with the same code and
 * content after a lost ack is [PublishResult.Published]; the same code with other content, or one
 * the author has since unpublished, is [PublishResult.CodeTaken]. Every Firebase exception is
 * mapped to a typed result here. The rules stamp the quota with the server's UTC day, so a denied
 * write from a device on another day is [PublishResult.ClockSkew] rather than
 * [PublishResult.Rejected].
 */
internal class FirestoreSharedPuzzleRepository(
    private val store: SharedPuzzleStore,
    private val currentUid: () -> String?,
    private val utcDay: () -> String = { LocalDate.now(ZoneOffset.UTC).toString() },
    private val serverDay: suspend () -> String? = ::firebaseServerDay,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : SharedPuzzleRepository {

    override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
        val uid = currentUid() ?: return PublishResult.SignedOut
        if (doc.authorUid != uid) return PublishResult.Rejected("authorUid is not the signed-in user")
        val day = utcDay()
        return try {
            val fields = SharedPuzzleDocCodec.encode(doc)
            when (val write = store.publish(doc.code.value, fields, uid, day)) {
                StoreWrite.Created -> PublishResult.Published(doc.code)
                // A retry after a lost ack finds its own doc. Changed content would leave friends
                // on the old one and an unpublished doc is closed to them: both count as taken.
                is StoreWrite.Exists -> {
                    val own = write.authorUid == uid &&
                        write.fields[SharedPuzzleDocCodec.FIELD_UNPUBLISHED] != true
                    val retry = own && SharedPuzzleDocCodec.sameContent(fields, write.fields)
                    // The author's lost-reply doc is online under a code only their draft held.
                    if (own && !retry) retire(doc.code)
                    if (retry) PublishResult.Published(doc.code) else PublishResult.CodeTaken
                }
                // The transaction read today's count at the cap, so it never sent the create.
                StoreWrite.CapReached -> PublishResult.DailyCapReached
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                e.isTransient() -> PublishResult.Offline
                e.isDenied() && deviceDayIsWrong(day) -> PublishResult.ClockSkew
                else -> PublishResult.Rejected(e.message)
            }
        }
    }

    /**
     * True only when the server's day was read within [SERVER_DAY_TIMEOUT] and differs from [day];
     * unreadable or too slow means unknown.
     */
    private suspend fun deviceDayIsWrong(day: String): Boolean = try {
        withTimeoutOrNull(SERVER_DAY_TIMEOUT) { serverDay() }?.let { it != day } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Best effort: a failed write leaves the orphan, and never blocks the author's publish. */
    private suspend fun retire(code: ShareCode) {
        try {
            store.unpublish(code.value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    override suspend fun unpublish(code: ShareCode): UnpublishResult {
        if (currentUid() == null) return UnpublishResult.Rejected("signed out")
        return try {
            store.unpublish(code.value)
            UnpublishResult.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isTransient()) UnpublishResult.Offline else UnpublishResult.Rejected(e.message)
        }
    }

    override suspend fun fetch(code: ShareCode): SharedFetchResult {
        val data = try {
            store.get(code.value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Reads are public, so any failure is the network or the server: worth a retry.
            return SharedFetchResult.Offline
        } ?: return SharedFetchResult.NotFound
        return SharedPuzzleDocCodec.decode(code, data)
    }

    override suspend fun listPublic(filter: CommunityFilter): CommunityListResult = try {
        val listed = store.list(filter.toQuery())
        // A doc this version cannot read is left out, so one bad doc never empties the list.
        CommunityListResult.Listed(
            listed.mapNotNull { (id, fields) ->
                val code = ShareCode.parse(id) ?: return@mapNotNull null
                (SharedPuzzleDocCodec.decode(code, fields) as? SharedFetchResult.Found)?.doc
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.isTransient()) CommunityListResult.Offline else CommunityListResult.Refused
    }

    /**
     * The rules allow a list only when the query itself keeps to public, still-published puzzles,
     * at most [COMMUNITY_LIST_LIMIT] of them. Every query is the equalities, then `difficulty in`,
     * then `createdAt >=` the window's start when the filter has a window, ordered by `createdAt`
     * descending (so a windowless query needs the same indexes). Each shape needs
     * its own composite index on `puzzles` in firestore.indexes.json, every field ascending but
     * `createdAt`, descending:
     *
     * | filter            | index                                                     |
     * |-------------------|-----------------------------------------------------------|
     * | none              | visibility, unpublished, createdAt                        |
     * | game              | visibility, unpublished, gameId, createdAt                |
     * | difficulty        | visibility, unpublished, difficulty, createdAt            |
     * | game + difficulty | visibility, unpublished, gameId, difficulty, createdAt    |
     */
    private fun CommunityFilter.toQuery() = CommunityQuery(
        equalTo = listOfNotNull(
            SharedPuzzleDocCodec.FIELD_VISIBILITY to SharedPuzzleDocCodec.VISIBILITY_PUBLIC,
            SharedPuzzleDocCodec.FIELD_UNPUBLISHED to false,
            gameId?.let { SharedPuzzleDocCodec.FIELD_GAME_ID to it },
        ),
        anyOf = Difficulty.Level.entries.filter { it in difficulties }.takeIf { it.isNotEmpty() }
            ?.let { SharedPuzzleDocCodec.FIELD_DIFFICULTY to it.map(SharedPuzzleDocCodec::difficultyName) },
        createdSince = windowMs?.let { Timestamp(Date(nowMs() - it)) },
        limit = COMMUNITY_LIST_LIMIT,
    )

    override suspend fun report(code: ShareCode): ReportResult {
        // Never sent signed out: the rules need a uid, and their refusal would read as already reported.
        val uid = currentUid() ?: return ReportResult.SignedOut
        return try {
            store.report(code.value, uid, reportFields(uid))
            ReportResult.Reported
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                // A second report, the author's own and a missing puzzle are all one denial.
                e.isDenied() -> ReportResult.AlreadyReported
                e.isTransient() -> ReportResult.Offline
                else -> ReportResult.Rejected(e.message)
            }
        }
    }
}

/** Failures a retry can fix; the rest (denied, malformed, missing) are final. */
internal fun Exception.isTransient(): Boolean = when (this) {
    is FirebaseNetworkException -> true
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        FirebaseFirestoreException.Code.INTERNAL,
        -> true

        else -> false
    }

    else -> false
}

internal fun Exception.isDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED
