package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow

/** The per-kind contract the sync engine drives; each kind of synced data implements it. */
interface SyncedKind {
    /** The kind name in the cloud space: `completions`, `drafts`, `published`, `received`, `boards` or `replays`. */
    val kind: String

    /** Ids with changes not yet acknowledged; the trigger source, distinct and debounced by the runner. */
    fun pendingIds(uid: String): Flow<Set<String>>

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
