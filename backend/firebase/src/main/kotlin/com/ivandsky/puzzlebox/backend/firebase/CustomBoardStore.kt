package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.TransactionOptions
import com.ivandsky.puzzlebox.game.RankOrder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The SDK seam for per-puzzle boards at `puzzles/{code}/entries/{uid}`: raw entry documents in
 * and out. Failures surface as Firebase exceptions for the repository to map.
 */
internal interface CustomBoardStore {

    /** Live documents of one board, ascending by [orderBy] then doc id, at most [limit]. */
    fun observeEntries(code: String, orderBy: List<String>, limit: Long): Flow<List<Map<String, Any?>>>

    /** Creates the uid's entry iff absent; returns the existing doc's fields, or null when created. */
    suspend fun createIfAbsent(code: String, uid: String, data: Map<String, Any?>): Map<String, Any?>?

    /** Server-side count() of the board's entries. */
    suspend fun count(code: String): Long
}

/** The entry fields a board query orders by, before the doc id; each pair has a deployed index. */
internal fun RankOrder.boardQueryFields(): List<String> = when (this) {
    RankOrder.TimeThenHints -> listOf(LeaderboardDocCodec.FIELD_TIME, LeaderboardDocCodec.FIELD_HINTS)
    RankOrder.HintsThenTime -> listOf(LeaderboardDocCodec.FIELD_HINTS, LeaderboardDocCodec.FIELD_TIME)
}

internal class FirestoreCustomBoardStore : CustomBoardStore {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun entries(code: String) =
        firestore.collection(PUZZLES).document(code).collection(ENTRIES)

    override fun observeEntries(
        code: String,
        orderBy: List<String>,
        limit: Long,
    ): Flow<List<Map<String, Any?>>> = callbackFlow {
        val ordered = orderBy.fold(entries(code) as Query) { query, field ->
            query.orderBy(field, Query.Direction.ASCENDING)
        }
        val registration = ordered
            .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().map { it.data.orEmpty() })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createIfAbsent(
        code: String,
        uid: String,
        data: Map<String, Any?>,
    ): Map<String, Any?>? {
        val doc = entries(code).document(uid)
        return firestore.runTransaction(SINGLE_ATTEMPT) { tx ->
            val existing = tx.get(doc)
            if (existing.exists()) {
                existing.data.orEmpty()
            } else {
                tx.set(doc, data)
                null
            }
        }.await()
    }

    override suspend fun count(code: String): Long =
        entries(code).count().get(AggregateSource.SERVER).await().count

    private companion object {
        const val PUZZLES = "puzzles"
        const val ENTRIES = "entries"

        // The SDK retries UNAVAILABLE with backoff; one attempt makes offline fail at once.
        val SINGLE_ATTEMPT: TransactionOptions = TransactionOptions.Builder().setMaxAttempts(1).build()
    }
}
