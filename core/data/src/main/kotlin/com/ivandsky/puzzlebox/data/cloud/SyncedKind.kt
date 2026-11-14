package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The per-kind contract the sync engine drives; each kind of synced data implements it. */
interface SyncedKind {
    /** The kind name in the cloud space: `completions`, `drafts`, `published`, `received`, `boards` or `replays`. */
    val kind: String

    /** Ids with changes not yet acknowledged: what a run has left to do, read by the engine after it. */
    fun pendingIds(uid: String): Flow<Set<String>>

    /**
     * What the runner's trigger watches: each pending id with a token that changes when the row's content does, so an
     * edit to a row that is already pending is a change too. The token must not change when an upload is acknowledged,
     * or a run would start the next. The default is for rows that cannot change while pending: every token is empty.
     */
    fun pendingStamps(uid: String): Flow<Map<String, String>> =
        pendingIds(uid).map { ids -> ids.associateWith { "" } }

    /** Claims the rows that have no owner yet for [uid]. */
    suspend fun adopt(uid: String)

    /** Encoded docs, tombstones included, that the cloud has not acknowledged. */
    suspend fun pending(uid: String): List<CloudDoc>

    /** Merges [docs] in one transaction and returns how many were restored. */
    suspend fun apply(uid: String, docs: List<CloudDoc>): Int

    /** Marks [uploaded] as stored in the cloud. */
    suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>)

    /** Clears the upload markers, so a later pass uploads everything again (used by erase). */
    suspend fun forget(uid: String)
}
