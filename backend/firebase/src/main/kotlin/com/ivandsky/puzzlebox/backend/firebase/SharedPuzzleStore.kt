package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.TransactionOptions
import com.ivandsky.puzzlebox.data.repository.DAILY_PUBLISH_CAP
import kotlinx.coroutines.tasks.await

/**
 * The SDK seam for shared puzzles: raw `puzzles/{code}` fields in and out, with the author's
 * daily quota kept in `authors/{uid}` and reports in `puzzles/{code}/reports/{uid}`. Failures
 * surface as Firebase exceptions for the repository to map.
 */
internal interface SharedPuzzleStore {

    /**
     * In one transaction: reports an existing `puzzles/{code}` with its fields, or stops at the
     * cap, or creates the doc from [fields] and bumps the author's count for [utcDay], naming
     * [code] as the bump's `last`.
     */
    suspend fun publish(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): StoreWrite

    /** Sets `unpublished` to true; a missing doc throws `NOT_FOUND`. */
    suspend fun unpublish(code: String)

    /** The doc's fields as the server holds them, or null when there is no such doc. */
    suspend fun get(code: String): Map<String, Any?>?

    /**
     * The puzzles [query] selects as code and fields, in the server's order, read from the server
     * and never the cache. The rules refuse a query whose own constraints do not keep it to
     * public, still-published puzzles with a limit of at most 30.
     */
    suspend fun list(query: CommunityQuery): List<Pair<String, Map<String, Any?>>>

    /**
     * Creates `puzzles/{code}/reports/{uid}` from [fields], or fails at once when offline. The
     * rules refuse a second report, the author's own and a code with no puzzle.
     */
    suspend fun report(code: String, uid: String, fields: Map<String, Any?>)
}

/** What a [SharedPuzzleStore.publish] transaction did. */
internal sealed interface StoreWrite {
    data object Created : StoreWrite
    data class Exists(val authorUid: String, val fields: Map<String, Any?>) : StoreWrite
    data object CapReached : StoreWrite
}

/**
 * One list read of `puzzles`, in the order a composite index holds its fields: every [equalTo],
 * then [anyOf], then `createdAt` at or after [createdSince] when there is one, newest first, at most
 * [limit] docs.
 */
internal data class CommunityQuery(
    val equalTo: List<Pair<String, Any>>,
    val anyOf: Pair<String, List<String>>?,
    val createdSince: Timestamp?,
    val limit: Int,
)

internal const val FIELD_AUTHOR_DAY = "day"
internal const val FIELD_AUTHOR_COUNT = "count"
internal const val FIELD_AUTHOR_LAST = "last"

/** The `authors/{uid}` count after one more create on [utcDay], or null when that day's cap is used up. */
internal fun nextPublishCount(author: Map<String, Any?>?, utcDay: String): Long? {
    val sameDay = author?.get(FIELD_AUTHOR_DAY) == utcDay
    val count = if (sameDay) (author?.get(FIELD_AUTHOR_COUNT) as? Number)?.toLong() ?: 0L else 0L
    return if (count >= DAILY_PUBLISH_CAP) null else count + 1
}

/**
 * The `authors/{uid}` doc after a create of [code]: the rules let one bump cover only that code.
 */
internal fun authorFields(utcDay: String, count: Long, code: String): Map<String, Any?> =
    mapOf(FIELD_AUTHOR_DAY to utcDay, FIELD_AUTHOR_COUNT to count, FIELD_AUTHOR_LAST to code)

/**
 * The `puzzles/{code}/reports/{uid}` doc: the rules take exactly these three keys, with the
 * reporter's own uid and the server's time.
 */
internal fun reportFields(uid: String): Map<String, Any?> = mapOf(
    "formatVersion" to 1,
    "uid" to uid,
    "createdAt" to FieldValue.serverTimestamp(),
)

internal class FirestoreSharedPuzzleStore : SharedPuzzleStore {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun puzzle(code: String) = firestore.collection(PUZZLES).document(code)

    private fun author(uid: String) = firestore.collection(AUTHORS).document(uid)

    override suspend fun publish(
        code: String,
        fields: Map<String, Any?>,
        uid: String,
        utcDay: String,
    ): StoreWrite {
        val puzzleDoc = puzzle(code)
        val authorDoc = author(uid)
        return firestore.runTransaction<StoreWrite>(SINGLE_ATTEMPT) { tx ->
            val existing = tx.get(puzzleDoc)
            if (existing.exists()) {
                return@runTransaction StoreWrite.Exists(
                    authorUid = existing.getString(SharedPuzzleDocCodec.FIELD_AUTHOR_UID).orEmpty(),
                    fields = existing.data.orEmpty(),
                )
            }
            val count = nextPublishCount(tx.get(authorDoc).data, utcDay)
                ?: return@runTransaction StoreWrite.CapReached
            tx.set(puzzleDoc, fields)
            tx.set(authorDoc, authorFields(utcDay, count, code))
            StoreWrite.Created
        }.await()
    }

    override suspend fun unpublish(code: String) {
        val puzzleDoc = puzzle(code)
        val found = firestore.runTransaction(SINGLE_ATTEMPT) { tx ->
            val snapshot = tx.get(puzzleDoc)
            // Already unpublished: nothing to write, so a retry after a lost ack succeeds.
            if (snapshot.exists() && snapshot.getBoolean(SharedPuzzleDocCodec.FIELD_UNPUBLISHED) != true) {
                tx.update(puzzleDoc, SharedPuzzleDocCodec.FIELD_UNPUBLISHED, true)
            }
            snapshot.exists()
        }.await()
        if (!found) throw FirebaseFirestoreException("no puzzle $code", FirebaseFirestoreException.Code.NOT_FOUND)
    }

    // From the server only: a cached copy could hide an unpublish.
    override suspend fun get(code: String): Map<String, Any?>? =
        puzzle(code).get(Source.SERVER).await().data

    // From the server only: a cached list would show unpublished puzzles and miss new ones.
    override suspend fun list(query: CommunityQuery): List<Pair<String, Map<String, Any?>>> {
        var puzzles: Query = firestore.collection(PUZZLES)
        query.equalTo.forEach { (field, value) -> puzzles = puzzles.whereEqualTo(field, value) }
        query.anyOf?.let { (field, values) -> puzzles = puzzles.whereIn(field, values) }
        query.createdSince?.let { puzzles = puzzles.whereGreaterThanOrEqualTo(SharedPuzzleDocCodec.FIELD_CREATED_AT, it) }
        return puzzles
            .orderBy(SharedPuzzleDocCodec.FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(query.limit.toLong())
            .get(Source.SERVER).await()
            .documents.map { it.id to it.data.orEmpty() }
    }

    // In a transaction, which commits on the server or fails: a plain set waits out an offline spell.
    override suspend fun report(code: String, uid: String, fields: Map<String, Any?>) {
        val reportDoc = puzzle(code).collection(REPORTS).document(uid)
        firestore.runTransaction(SINGLE_ATTEMPT) { tx ->
            tx.set(reportDoc, fields)
            Unit
        }.await()
    }

    private companion object {
        const val PUZZLES = "puzzles"
        const val AUTHORS = "authors"
        const val REPORTS = "reports"

        // The SDK retries UNAVAILABLE with backoff; one attempt makes offline fail at once.
        val SINGLE_ATTEMPT: TransactionOptions = TransactionOptions.Builder().setMaxAttempts(1).build()
    }
}
