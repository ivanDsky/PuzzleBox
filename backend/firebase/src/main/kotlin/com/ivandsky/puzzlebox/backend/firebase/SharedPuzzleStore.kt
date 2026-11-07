package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.TransactionOptions
import kotlinx.coroutines.tasks.await

/**
 * The SDK seam for shared puzzles: raw `puzzles/{code}` fields in and out, with the author's
 * daily quota kept in `authors/{uid}`. Failures surface as Firebase exceptions for the
 * repository to map.
 */
internal interface SharedPuzzleStore {

    /**
     * In one transaction: reports an existing `puzzles/{code}`, or stops at the cap, or creates
     * the doc from [fields] and bumps the author's count for [utcDay].
     */
    suspend fun publish(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): StoreWrite

    /** Sets `unpublished` to true; a missing doc throws `NOT_FOUND`. */
    suspend fun unpublish(code: String)

    /** The doc's fields as the server holds them, or null when there is no such doc. */
    suspend fun get(code: String): Map<String, Any?>?
}

/** What a [SharedPuzzleStore.publish] transaction did. */
internal sealed interface StoreWrite {
    data object Created : StoreWrite
    data class Exists(val authorUid: String) : StoreWrite
    data object CapReached : StoreWrite
}

internal const val DAILY_PUBLISH_CAP = 20L
internal const val FIELD_AUTHOR_DAY = "day"
internal const val FIELD_AUTHOR_COUNT = "count"

/** The `authors/{uid}` count after one more create on [utcDay], or null when that day's cap is used up. */
internal fun nextPublishCount(author: Map<String, Any?>?, utcDay: String): Long? {
    val sameDay = author?.get(FIELD_AUTHOR_DAY) == utcDay
    val count = if (sameDay) (author?.get(FIELD_AUTHOR_COUNT) as? Number)?.toLong() ?: 0L else 0L
    return if (count >= DAILY_PUBLISH_CAP) null else count + 1
}

internal fun authorFields(utcDay: String, count: Long): Map<String, Any?> =
    mapOf(FIELD_AUTHOR_DAY to utcDay, FIELD_AUTHOR_COUNT to count)

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
                return@runTransaction StoreWrite.Exists(existing.getString(SharedPuzzleDocCodec.FIELD_AUTHOR_UID).orEmpty())
            }
            val count = nextPublishCount(tx.get(authorDoc).data, utcDay)
                ?: return@runTransaction StoreWrite.CapReached
            tx.set(puzzleDoc, fields)
            tx.set(authorDoc, authorFields(utcDay, count))
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

    private companion object {
        const val PUZZLES = "puzzles"
        const val AUTHORS = "authors"

        // The SDK retries UNAVAILABLE with backoff; one attempt makes offline fail at once.
        val SINGLE_ATTEMPT: TransactionOptions = TransactionOptions.Builder().setMaxAttempts(1).build()
    }
}
