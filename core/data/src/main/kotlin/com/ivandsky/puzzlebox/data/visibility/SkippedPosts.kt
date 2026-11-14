package com.ivandsky.puzzlebox.data.visibility

import com.ivandsky.puzzlebox.datastore.SyncStateDataSource

/**
 * The solves a player made while Hidden (ledger L264): they never post, even after a switch to Board. The tier doc has no
 * room for a per-solve fact, so the device keeps the puzzle refs itself, per uid, across process death.
 */
interface SkippedPosts {

    suspend fun contains(uid: String, puzzleRef: String): Boolean

    suspend fun add(uid: String, puzzleRef: String)
}

/** The refs in the sync-state file, which backup and device transfer exclude. */
internal class DataStoreSkippedPosts(private val source: SyncStateDataSource) : SkippedPosts {

    override suspend fun contains(uid: String, puzzleRef: String): Boolean = puzzleRef in source.skippedPosts(uid)

    override suspend fun add(uid: String, puzzleRef: String) {
        source.addSkippedPost(uid, puzzleRef)
    }
}
