package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [DailyReplayRepository] over `leaderboards/{boardId}/replays/{uid}` = `{uid, blob, formatVersion, createdAtMs}`, the
 * only shape the rules accept. The size cap is checked before any write. A denied or failed read is a null, never an
 * error across the seam; the collection is never listed (the rules deny it).
 */
internal class FirestoreDailyReplayRepository(
    private val store: ReplayDocStore = FirestoreReplayDocStore(),
    private val currentUid: () -> String?,
    private val clock: Clock = Clock.systemUTC(),
    private val timeout: Duration = CALL_TIMEOUT,
) : DailyReplayRepository {

    override suspend fun publish(board: LeaderboardRef, blob: ByteArray): Boolean {
        if (blob.isEmpty() || blob.size > DailyReplayRepository.MAX_BYTES) return false
        val uid = currentUid() ?: return false
        return try {
            // Offline the SDK queues the write and never answers; that is not a published replay.
            withTimeoutOrNull(timeout) {
                store.put(board.boardId, uid, encodeReplayDoc(uid, blob, clock.millis()))
                true
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fetch(board: LeaderboardRef, uid: String): ByteArray? = try {
        withTimeoutOrNull(timeout) { store.get(board.boardId, uid)?.let(::decodeReplayBlob) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override suspend fun delete(board: LeaderboardRef) {
        val uid = currentUid() ?: return
        try {
            withTimeoutOrNull(timeout) { store.delete(board.boardId, uid) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort: the owner can delete again, and the rules bound what a leftover doc exposes.
        }
    }

    private companion object {
        val CALL_TIMEOUT: Duration = 10.seconds
    }
}

internal const val REPLAY_FORMAT_VERSION = 1L

/** The replay doc exactly as the rules accept it: `uid`, a `Blob`, `formatVersion == 1`, `createdAtMs > 0`. */
internal fun encodeReplayDoc(uid: String, blob: ByteArray, createdAtMs: Long): Map<String, Any> = mapOf(
    "uid" to uid,
    "blob" to Blob.fromBytes(blob),
    "formatVersion" to REPLAY_FORMAT_VERSION,
    "createdAtMs" to createdAtMs,
)

/** The stored bytes; null for a doc whose blob is missing, mistyped or empty. */
internal fun decodeReplayBlob(fields: Map<String, Any?>): ByteArray? =
    (fields["blob"] as? Blob)?.toBytes()?.takeIf { it.isNotEmpty() }

/** One replay doc per (board, uid); lets the repository be tested without Firestore. */
internal interface ReplayDocStore {
    suspend fun put(boardId: String, uid: String, fields: Map<String, Any>)

    /** Null when the doc is absent. Throws when the read is denied or fails. */
    suspend fun get(boardId: String, uid: String): Map<String, Any?>?

    suspend fun delete(boardId: String, uid: String)
}

internal class FirestoreReplayDocStore : ReplayDocStore {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun doc(boardId: String, uid: String) =
        firestore.collection("leaderboards").document(boardId).collection("replays").document(uid)

    override suspend fun put(boardId: String, uid: String, fields: Map<String, Any>) {
        doc(boardId, uid).set(fields).await()
    }

    override suspend fun get(boardId: String, uid: String): Map<String, Any?>? {
        val snapshot = doc(boardId, uid).get(Source.SERVER).await()
        return if (snapshot.exists()) snapshot.data else null
    }

    override suspend fun delete(boardId: String, uid: String) {
        doc(boardId, uid).delete().await()
    }
}
