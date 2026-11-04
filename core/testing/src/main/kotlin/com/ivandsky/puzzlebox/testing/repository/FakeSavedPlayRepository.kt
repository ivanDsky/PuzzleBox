package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.PlaySlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [SavedPlayRepository] that never evicts (eviction is a Room-adapter detail).
 * [rows] exposes every stored blob for assertions; it passes the same contract test as the
 * Room adapter, so it cannot drift (A12).
 */
class FakeSavedPlayRepository : SavedPlayRepository {

    private val state = MutableStateFlow<Map<PlaySlot, ByteArray>>(emptyMap())

    /** Every stored slot and its blob, as last written. */
    val rows: Map<PlaySlot, ByteArray> get() = state.value

    override suspend fun save(slot: PlaySlot, blob: ByteArray) {
        state.update { (it - slot) + (slot to blob.copyOf()) }
    }

    override suspend fun load(slot: PlaySlot): ByteArray? = state.value[slot]?.copyOf()

    override suspend fun delete(slot: PlaySlot) {
        state.update { it - slot }
    }

    override fun observeInProgress(): Flow<Set<PlaySlot>> = state.map { it.keys }.distinctUntilChanged()
}
