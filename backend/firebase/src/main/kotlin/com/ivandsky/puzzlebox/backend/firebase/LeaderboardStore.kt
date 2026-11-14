package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.game.RankOrder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** One listener answer: the raw docs, and whether the device cache (not the server) produced it. */
internal data class BoardDocs(val docs: List<Map<String, Any?>>, val fromCache: Boolean)

/**
 * The SDK seam for the leaderboard (design §3): raw Firestore documents in, no
 * exception escaping un-mapped. Everything Firebase-typed stops here.
 */
internal interface LeaderboardStore {

    /** Live documents of one board, server-ordered by [order]'s [boardQueryFields]. */
    fun observeEntries(boardId: String, order: RankOrder): Flow<BoardDocs>

    /**
     * One-shot read of the same query [observeEntries] listens to, from the server only: offline it
     * throws. A default-source read would answer an empty board from the cache, and a Final board
     * is stored for good on its first answer.
     */
    suspend fun fetchEntries(boardId: String, order: RankOrder): List<Map<String, Any?>>

    /** Server-side count of the whole board (a billed aggregate, not a document read per row). */
    suspend fun count(boardId: String): Long

    /** Creates the uid's entry doc iff absent; false = first write stands (A4/A5). */
    suspend fun createIfAbsent(boardId: String, uid: String, data: Map<String, Any?>): Boolean
}

internal class FirestoreLeaderboardStore : LeaderboardStore {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun entries(boardId: String) =
        firestore.collection(COLLECTION).document(boardId).collection(ENTRIES)

    private fun page(boardId: String, order: RankOrder) = order.boardQueryFields()
        .fold(entries(boardId) as Query) { query, field -> query.orderBy(field, Query.Direction.ASCENDING) }
        .limit(MAX_BOARD_SIZE)

    override suspend fun fetchEntries(boardId: String, order: RankOrder): List<Map<String, Any?>> =
        page(boardId, order).get(Source.SERVER).await().documents.map { it.data.orEmpty() }

    override suspend fun count(boardId: String): Long =
        entries(boardId).count().get(AggregateSource.SERVER).await().count

    override fun observeEntries(boardId: String, order: RankOrder): Flow<BoardDocs> = callbackFlow {
        // INCLUDE: the server confirming an unchanged (still empty) board only flips the metadata, and that flip must be seen.
        val registration = page(boardId, order)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    BoardDocs(
                        docs = snapshot?.documents.orEmpty().map { it.data.orEmpty() },
                        fromCache = snapshot?.metadata?.isFromCache ?: false,
                    ),
                )
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

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
        const val MAX_BOARD_SIZE = BoardPage.PAGE_SIZE.toLong()
    }
}
