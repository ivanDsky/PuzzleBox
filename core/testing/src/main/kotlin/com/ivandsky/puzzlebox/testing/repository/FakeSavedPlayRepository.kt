package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.PlaySlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [SavedPlayRepository]. It never evicts a [PlaySlot.Daily] (the per-game daily cap
 * is a Room-adapter detail), but keeps only the newest [CUSTOM_CAP] [PlaySlot.Custom] slots,
 * since the contract pins that a friend save never evicts a daily (A13). [rows] exposes every
 * stored blob for assertions; it passes the same contract test as the Room adapter, so it
 * cannot drift (A12).
 */
class FakeSavedPlayRepository : SavedPlayRepository {

    private class Row(val blob: ByteArray, val elapsedMs: Long)

    private val state = MutableStateFlow<Map<PlaySlot, Row>>(emptyMap())

    /** Every stored slot and its blob, as last written. */
    val rows: Map<PlaySlot, ByteArray> get() = state.value.mapValues { it.value.blob }

    override suspend fun save(slot: PlaySlot, blob: ByteArray, elapsedMs: Long) {
        state.update { rows ->
            val saved = (rows - slot) + (slot to Row(blob.copyOf(), elapsedMs))
            val evicted = saved.keys.filterIsInstance<PlaySlot.Custom>().dropLast(CUSTOM_CAP)
            saved - evicted.toSet()
        }
    }

    override suspend fun load(slot: PlaySlot): ByteArray? = state.value[slot]?.blob?.copyOf()

    override suspend fun delete(slot: PlaySlot) {
        state.update { it - slot }
    }

    override fun observeInProgress(): Flow<Map<PlaySlot, Long>> =
        state.map { rows -> rows.mapValues { it.value.elapsedMs } }.distinctUntilChanged()

    private companion object {
        const val CUSTOM_CAP = 8
    }
}
