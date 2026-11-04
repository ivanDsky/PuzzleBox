package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.PlaySlot
import kotlinx.coroutines.flow.Flow

/**
 * Saved in-progress plays (A10), one opaque session blob per [PlaySlot]. How many slots
 * are kept is an adapter detail: an adapter may evict the oldest slots, never the newest.
 */
interface SavedPlayRepository {

    /** Upserts [blob] for [slot] and stamps it as the most recently updated slot. */
    suspend fun save(slot: PlaySlot, blob: ByteArray)

    /** The saved blob of [slot], or null when none is saved. */
    suspend fun load(slot: PlaySlot): ByteArray?

    /** Removes the saved play of [slot]; a no-op when none exists. */
    suspend fun delete(slot: PlaySlot)

    /** Every slot with a saved play; re-emits on each save, eviction and delete. */
    fun observeInProgress(): Flow<Set<PlaySlot>>
}
