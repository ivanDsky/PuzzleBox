package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The sync state over its own DataStore file. [lastUid] is a StateFlow, so it needs a value before anyone
 * collects: it is read once here, blocking for that single small file read, because a wrong null would make
 * a signed-out player look like a fresh install. This store is the only writer of the file, so later writes
 * update the flow directly, after they are persisted.
 */
internal class DataStoreSyncStateStore(
    private val source: SyncStateDataSource,
) : SyncStateStore {

    private val last = MutableStateFlow(runBlocking { source.lastUid.first() })

    override val lastUid: StateFlow<String?> = last

    override suspend fun cursor(uid: String, kind: String): Long? = source.cursor(uid, kind)

    override suspend fun setCursor(uid: String, kind: String, cursorMs: Long) {
        source.setCursor(uid, kind, cursorMs)
    }

    override suspend fun setLastUid(uid: String) {
        source.setLastUid(uid)
        last.value = uid
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
