package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore implementation of [CloudSpace] over [CloudSpaceStore] (ADR-0029 §2). The signed-in uid is read right before
 * every store call, including each batch of a long write, and a mismatch is [CloudResult.WrongAccount] with the store
 * untouched. Every store call is bounded by [timeout]: a commit offline is queued by the SDK and never answers, so a
 * timeout is [CloudResult.Offline] (unknown, safe to repeat: every write is an idempotent set-merge or delete).
 * `UNAVAILABLE` and the other codes a retry can fix are Offline, `RESOURCE_EXHAUSTED` is [CloudResult.Exhausted] and
 * never retried here, and anything else, `PERMISSION_DENIED` first, is [CloudResult.Denied]. Only
 * [CancellationException] escapes.
 */
internal class FirestoreCloudSpace(
    private val store: CloudSpaceStore,
    private val currentUid: () -> String?,
    private val timeout: Duration = CALL_TIMEOUT,
) : CloudSpace {

    override suspend fun changedSince(uid: String, kind: String, cursorMs: Long?): CloudResult<List<CloudDoc>> =
        guarded(uid) { store.read(uid, kind, cursorMs) }

    override suspend fun put(uid: String, kind: String, docs: List<CloudDoc>): CloudResult<Unit> =
        commitAll(uid, docs.chunked(CLOUD_BATCH_LIMIT)) { batch -> store.commitPut(uid, kind, batch) }

    override suspend fun delete(uid: String, kind: String, ids: List<String>): CloudResult<Unit> =
        commitAll(uid, ids.chunked(CLOUD_BATCH_LIMIT)) { batch -> store.commitDelete(uid, kind, batch) }

    override suspend fun deleteAll(uid: String, kinds: List<String>): CloudResult<Unit> {
        for (kind in kinds) {
            val ids = when (val listed = guarded(uid) { store.ids(uid, kind) }) {
                is CloudResult.Ok -> listed.value
                else -> return listed.failure()
            }
            val deleted = delete(uid, kind, ids)
            if (deleted !is CloudResult.Ok) return deleted
        }
        return CloudResult.Ok(Unit)
    }

    /** Commits each of [batches] in order and stops at the first one that is not [CloudResult.Ok]. */
    private suspend fun <B> commitAll(uid: String, batches: List<B>, commit: suspend (B) -> Unit): CloudResult<Unit> {
        for (batch in batches) {
            val result = guarded(uid) { commit(batch) }
            if (result !is CloudResult.Ok) return result
        }
        return CloudResult.Ok(Unit)
    }

    /** The uid check, then [block] under [timeout], with every failure as a typed result. */
    private suspend fun <T> guarded(uid: String, block: suspend () -> T): CloudResult<T> {
        if (currentUid() != uid) return CloudResult.WrongAccount
        return try {
            withTimeoutOrNull(timeout) { block() }?.let { CloudResult.Ok(it) } ?: CloudResult.Offline
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toCloudFailure()
        }
    }

    private fun CloudResult<*>.failure(): CloudResult<Nothing> = when (this) {
        CloudResult.Offline -> CloudResult.Offline
        CloudResult.Exhausted -> CloudResult.Exhausted
        CloudResult.WrongAccount -> CloudResult.WrongAccount
        else -> CloudResult.Denied
    }

    private companion object {
        val CALL_TIMEOUT: Duration = 15.seconds
    }
}

private fun Exception.toCloudFailure(): CloudResult<Nothing> = when {
    this is FirebaseNetworkException -> CloudResult.Offline
    this is FirebaseFirestoreException -> when (code) {
        Code.RESOURCE_EXHAUSTED -> CloudResult.Exhausted
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED, Code.INTERNAL -> CloudResult.Offline
        else -> CloudResult.Denied
    }

    // A failure no retry can fix (refused, malformed, a value that is not plain) pauses sync as a refusal.
    else -> CloudResult.Denied
}
