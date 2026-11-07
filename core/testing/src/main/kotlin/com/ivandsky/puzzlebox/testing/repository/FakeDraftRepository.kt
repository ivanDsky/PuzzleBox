package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [DraftRepository]. [rows] exposes every stored draft by id for assertions; it passes
 * the same contract test as the Room adapter, so it cannot drift (S15). Blobs are copied in and
 * out, so callers can never mutate a stored draft.
 */
class FakeDraftRepository : DraftRepository {

    private val state = MutableStateFlow<Map<String, Draft>>(emptyMap())

    /** Every stored draft by id, as last written. */
    val rows: Map<String, Draft> get() = state.value

    override fun observeAll(): Flow<List<Draft>> =
        state.map { rows -> rows.values.sortedByDescending { it.updatedAt }.map { it.detached() } }.distinctUntilChanged()

    override fun observe(id: String): Flow<Draft?> = state.map { it[id]?.detached() }.distinctUntilChanged()

    override suspend fun get(id: String): Draft? = state.value[id]?.detached()

    override suspend fun upsert(draft: Draft) {
        val stored = draft.copy(updatedAt = draft.updatedAt.truncatedTo(ChronoUnit.MILLIS)).detached()
        state.update { it + (draft.id to stored) }
    }

    override suspend fun delete(id: String) {
        state.update { it - id }
    }

    private fun Draft.detached() = copy(blob = blob.copyOf())
}
