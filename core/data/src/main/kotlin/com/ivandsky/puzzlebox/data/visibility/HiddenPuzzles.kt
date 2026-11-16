package com.ivandsky.puzzlebox.data.visibility

import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The community puzzles this player reported, kept on this device so they stay out of the lists.
 * Not synced and not per account: a report hides the puzzle for whoever uses the device. Hiding
 * only filters lists; a hidden code still opens when typed.
 */
interface HiddenPuzzles {

    suspend fun codes(): Set<ShareCode>

    /** The hidden codes, again each time [add] hides another; lists that drop hidden puzzles follow it. */
    fun observe(): Flow<Set<ShareCode>>

    suspend fun add(code: ShareCode)
}

/** The codes in the sync-state file, which backup and device transfer exclude. */
internal class DataStoreHiddenPuzzles(private val source: SyncStateDataSource) : HiddenPuzzles {

    override suspend fun codes(): Set<ShareCode> = source.hiddenPuzzles().mapNotNullTo(mutableSetOf(), ShareCode::parse)

    override fun observe(): Flow<Set<ShareCode>> =
        source.observeHiddenPuzzles().map { codes -> codes.mapNotNullTo(mutableSetOf(), ShareCode::parse) }

    override suspend fun add(code: ShareCode) {
        source.addHiddenPuzzle(code.value)
    }
}
