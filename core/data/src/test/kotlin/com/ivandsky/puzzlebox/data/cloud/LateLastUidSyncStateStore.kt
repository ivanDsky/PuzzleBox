package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.testing.cloud.FakeSyncStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** A store whose last uid has not been read yet: [lastUid] emits nothing until [load], like DataStore before its first read. */
internal class LateLastUidSyncStateStore(
    private val delegate: FakeSyncStateStore = FakeSyncStateStore(),
) : SyncStateStore by delegate {

    private val loaded = MutableSharedFlow<String?>(replay = 1)

    override val lastUid: Flow<String?> = loaded

    /** The first read lands with [uid]. */
    fun load(uid: String?) {
        loaded.tryEmit(uid)
    }
}
