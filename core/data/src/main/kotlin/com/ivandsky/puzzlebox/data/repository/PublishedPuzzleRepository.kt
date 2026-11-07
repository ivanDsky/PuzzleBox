package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Read/write port for the author's published puzzles; instants are kept to the millisecond. */
interface PublishedPuzzleRepository {
    /** Newest createdAt first. */
    fun observeAll(): Flow<List<PublishedPuzzle>>

    /** The row for [code], or null while none exists. */
    fun observe(code: ShareCode): Flow<PublishedPuzzle?>

    /** Inserts [puzzle] or replaces the row with the same code. */
    suspend fun upsert(puzzle: PublishedPuzzle)

    /** Marks the row for [code] withdrawn; an unknown code is a no-op. */
    suspend fun markUnpublished(code: ShareCode)

    /** Caches [count] solves fetched [at] on the row for [code]; an unknown code is a no-op. */
    suspend fun updateSolves(code: ShareCode, count: Int, at: Instant)
}
