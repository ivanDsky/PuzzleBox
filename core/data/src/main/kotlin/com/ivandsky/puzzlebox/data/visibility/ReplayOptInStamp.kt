package com.ivandsky.puzzlebox.data.visibility

import com.ivandsky.puzzlebox.datastore.SyncStateDataSource

/**
 * When this device first saw a player's own tier at Replays (A20: the opt-in covers future solves only). The tier lives
 * in the visibility doc, which has no room for a time, so the device keeps the instant itself and a solve made before
 * it never publishes. Unset means no opt-in is known here, which also covers an opt-in made on another device until
 * this one observes it.
 */
interface ReplayOptInStamp {

    /** The epoch ms of [uid]'s current opt-in as first observed here, or null when there is none. */
    suspend fun since(uid: String): Long?

    /** Keeps an existing stamp: only the first observation of an opt-in counts, however often it is seen again. */
    suspend fun markIfAbsent(uid: String, atMs: Long)

    /** Forgets the opt-in, so the next rise to Replays is stamped afresh. */
    suspend fun clear(uid: String)
}

/** The stamp in the sync-state file, which backup and device transfer exclude: a restored stamp would pre-date solves. */
internal class DataStoreReplayOptInStamp(
    private val source: SyncStateDataSource,
) : ReplayOptInStamp {

    override suspend fun since(uid: String): Long? = source.replaysSince(uid)

    override suspend fun markIfAbsent(uid: String, atMs: Long) {
        source.markReplaysSince(uid, atMs)
    }

    override suspend fun clear(uid: String) {
        source.clearReplaysSince(uid)
    }
}
