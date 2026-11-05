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

    private class Row(val blob: ByteArray, val elapsedMs: Long)

    private val state = MutableStateFlow<Map<PlaySlot, Row>>(emptyMap())

    /** Every stored slot and its blob, as last written. */
    val rows: Map<PlaySlot, ByteArray> get() = state.value.mapValues { it.value.blob }

    override suspend fun save(slot: PlaySlot, blob: ByteArray, elapsedMs: Long) {
        state.update { (it - slot) + (slot to Row(blob.copyOf(), elapsedMs)) }
    }

    override suspend fun load(slot: PlaySlot): ByteArray? = state.value[slot]?.blob?.copyOf()

    override suspend fun delete(slot: PlaySlot) {
        state.update { it - slot }
    }

    override fun observeInProgress(): Flow<Map<PlaySlot, Long>> =
        state.map { rows -> rows.mapValues { it.value.elapsedMs } }.distinctUntilChanged()
}
