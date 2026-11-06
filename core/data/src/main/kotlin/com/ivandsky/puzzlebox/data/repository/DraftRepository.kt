package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.Draft
import kotlinx.coroutines.flow.Flow

/** Read/write port for the author's local drafts (A5): features never touch Room directly. */
interface DraftRepository {
    /** Newest updatedAt first. */
    fun observeAll(): Flow<List<Draft>>

    /** The draft with [id], or null while none exists. */
    fun observe(id: String): Flow<Draft?>

    suspend fun get(id: String): Draft?

    /** Inserts [draft] or replaces the one with the same id. */
    suspend fun upsert(draft: Draft)

    /** Removes the draft with [id]; a missing id is a no-op. */
    suspend fun delete(id: String)
}
