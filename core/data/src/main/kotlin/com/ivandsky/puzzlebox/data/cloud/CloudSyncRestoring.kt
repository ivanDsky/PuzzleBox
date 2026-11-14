package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * True while this account's first restore on this device is pulling (A34): the one definition screens use to say
 * their data is coming back instead of showing first-time empty copy.
 */
val CloudSync.restoring: Flow<Boolean>
    get() = state.map { it is CloudSyncState.Syncing && it.firstRestore }.distinctUntilChanged()
