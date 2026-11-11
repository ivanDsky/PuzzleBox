package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.cloud.CloudDoc

/**
 * The SDK seam for the account's cloud space (ADR-0029 §2): plain values in and out, so [FirestoreCloudSpace] and its
 * tests exercise the uid check, timeouts and error mapping without the SDK. Failures surface as Firebase exceptions
 * for the adapter to map; a write the rules refuse is `PERMISSION_DENIED`. Every call is addressed by the account's
 * `users/{uid}/{kind}` path and is not retried here.
 */
internal interface CloudSpaceStore {

    /** The docs of [kind] with `updatedAt >= cursorMs`, or all of them when [cursorMs] is null; read from the server. */
    suspend fun read(uid: String, kind: String, cursorMs: Long?): List<CloudDoc>

    /** The ids of every doc of [kind], read from the server. */
    suspend fun ids(uid: String, kind: String): List<String>

    /**
     * One batch: set-merges each of [docs] (at most [CLOUD_BATCH_LIMIT]) and stamps `updatedAt` with the server time.
     * Offline the SDK queues the batch and never returns, so the caller bounds it with a timeout.
     */
    suspend fun commitPut(uid: String, kind: String, docs: List<CloudDoc>)

    /** One batch: deletes [ids] (at most [CLOUD_BATCH_LIMIT]); an id that does not exist is not an error. */
    suspend fun commitDelete(uid: String, kind: String, ids: List<String>)
}

/** Firestore refuses a batch of more than 500 writes; this leaves room under it. */
internal const val CLOUD_BATCH_LIMIT = 400
