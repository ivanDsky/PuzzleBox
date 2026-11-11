package com.ivandsky.puzzlebox.data.cloud

/** What the sync engine is doing for the signed-in account; the Google row renders it. */
sealed interface CloudSyncState {
    /** Nobody is signed in, so nothing syncs. */
    data object SignedOut : CloudSyncState

    /** A pass is running; [firstRestore] is true while this account's first restore on this device is still pulling. */
    data class Syncing(val firstRestore: Boolean) : CloudSyncState

    /** The last pass finished at [atMs] (epoch ms) with nothing left to send. */
    data class Synced(val atMs: Long) : CloudSyncState

    /** The cloud is unreachable; [lastAtMs] is the last successful pass, or null when there never was one. */
    data class Offline(val lastAtMs: Long?) : CloudSyncState

    /** Sync stopped for [reason] and resumes without the user once the cause clears; [lastAtMs] as in [Offline]. */
    data class Paused(val reason: PauseReason, val lastAtMs: Long?) : CloudSyncState
}
