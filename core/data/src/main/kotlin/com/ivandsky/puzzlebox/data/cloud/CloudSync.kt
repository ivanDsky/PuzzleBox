package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The engine API features call (ADR-0029 decisions 3 and 9). Feature Koin modules resolve `getOrNull<CloudSync>()`;
 * null (flag off) means today's UI exactly.
 */
interface CloudSync {
    val state: StateFlow<CloudSyncState>

    /** Emits once per account per device, when its first restore has finished and brought something. */
    val restored: Flow<RestoreSummary>

    /** Runs a sync pass now instead of waiting for the next trigger. */
    suspend fun syncNow()

    /** Wipes the account's cloud space, forgets upload markers and cursors, and signs out. */
    suspend fun eraseCloud(): CloudResult<Unit>
}
