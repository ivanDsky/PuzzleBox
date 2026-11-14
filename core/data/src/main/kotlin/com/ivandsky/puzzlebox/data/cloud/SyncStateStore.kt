package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow

/**
 * Per-account sync bookkeeping that never enters a backup (ADR-0029 decision 7). A uid's values are invisible to
 * every other uid.
 */
interface SyncStateStore {
    /** The pull cursor (epoch millis) for [uid]'s [kind], or null before the first pull. */
    suspend fun cursor(uid: String, kind: String): Long?

    suspend fun setCursor(uid: String, kind: String, cursorMs: Long)

    /** The uid that last signed in on this install, or null on a fresh one; emits again on every change. Survives [forget]. */
    val lastUid: Flow<String?>

    /** Called only by `SignedInOwnerScope`; the engine reads [lastUid] and never writes it. */
    suspend fun setLastUid(uid: String)

    /** Whether the restore notice was already shown to [uid]. */
    suspend fun restoreShown(uid: String): Boolean

    suspend fun markRestoreShown(uid: String)

    /** Adds [n] to [uid]'s write counter for [day] and returns that day's total; a new [day] starts again from [n]. */
    suspend fun countWrites(uid: String, day: String, n: Int): Int

    /** Drops [uid]'s cursors and restore flag. The last uid and the write counter stay. */
    suspend fun forget(uid: String)
}
