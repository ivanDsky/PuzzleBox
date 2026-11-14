package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The sync state over its own DataStore file. Nothing is read at construction: [lastUid] is the file's own flow, so
 * the first read happens when someone collects it, and a write shows up in it only after it is persisted.
 */
internal class DataStoreSyncStateStore(
    private val source: SyncStateDataSource,
) : SyncStateStore {

    override val lastUid: Flow<String?> = source.lastUid.distinctUntilChanged()

    override suspend fun cursor(uid: String, kind: String): Long? = source.cursor(uid, kind)

    override suspend fun setCursor(uid: String, kind: String, cursorMs: Long) {
        source.setCursor(uid, kind, cursorMs)
    }

    override suspend fun setLastUid(uid: String) {
        source.setLastUid(uid)
    }

    override suspend fun restoreShown(uid: String): Boolean = source.restoreShown(uid)

    override suspend fun markRestoreShown(uid: String) {
        source.markRestoreShown(uid)
    }

    override suspend fun countWrites(uid: String, day: String, n: Int): Int = source.countWrites(uid, day, n)

    override suspend fun forget(uid: String) {
        source.forget(uid)
    }
}
