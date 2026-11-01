package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The SDK seam for the leaderboard (design §3): raw Firestore documents in, no
 * exception escaping un-mapped. Everything Firebase-typed stops here.
 */
internal interface LeaderboardStore {

    /** Live, server-ordered (timeMs, then hints) documents of one board. */
    fun observeEntries(boardId: String): Flow<List<Map<String, Any?>>>

    /** Creates the uid's entry doc iff absent; false = first write stands (A4/A5). */
    suspend fun createIfAbsent(boardId: String, uid: String, data: Map<String, Any?>): Boolean
}

internal class FirestoreLeaderboardStore : LeaderboardStore {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun entries(boardId: String) =
        firestore.collection(COLLECTION).document(boardId).collection(ENTRIES)

    override fun observeEntries(boardId: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val registration = entries(boardId)
            .orderBy(FIELD_TIME, Query.Direction.ASCENDING)
            .orderBy(FIELD_HINTS, Query.Direction.ASCENDING)
            .limit(MAX_BOARD_SIZE)
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
        boardId: String,
        uid: String,
        data: Map<String, Any?>,
    ): Boolean {
        val doc = entries(boardId).document(uid)
        return firestore.runTransaction { tx ->
            if (tx.get(doc).exists()) {
                false
            } else {
                tx.set(doc, data)
                true
            }
        }.await()
    }

    companion object {
        const val COLLECTION = "leaderboards"
        const val ENTRIES = "entries"
        const val FIELD_TIME = "timeMs"
        const val FIELD_HINTS = "hints"
        const val MAX_BOARD_SIZE = 100L
    }
}
