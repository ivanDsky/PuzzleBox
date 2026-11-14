package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * True while this account's first restore on this device is pulling (A34): the one definition screens use to say
 * their data is coming back instead of showing first-time empty copy. A null receiver (no [CloudSync] bound, the
 * flag is off) is never restoring.
 */
val CloudSync?.restoring: Flow<Boolean>
    get() = this?.state
        ?.map { it is CloudSyncState.Syncing && it.firstRestore }
        ?.distinctUntilChanged()
        ?: flowOf(false)
