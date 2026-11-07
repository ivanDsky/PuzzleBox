package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow

/** Read/write port for friends' puzzles opened on this device; [ReceivedPuzzle.openedAt] is kept to the millisecond. */
interface ReceivedPuzzleRepository {
    /** Newest openedAt first. */
    fun observeAll(): Flow<List<ReceivedPuzzle>>

    /** The row for [code], or null while none exists. */
    fun observe(code: ShareCode): Flow<ReceivedPuzzle?>

    suspend fun get(code: ShareCode): ReceivedPuzzle?

    /** Inserts [puzzle] or replaces the row with the same code. */
    suspend fun upsert(puzzle: ReceivedPuzzle)

    /** Removes the row for [code]; an unknown code is a no-op. */
    suspend fun delete(code: ShareCode)

    /** Sets the post [state] and [attempts] on the row for [code]; an unknown code is a no-op. */
    suspend fun updatePost(code: ShareCode, state: PostState, attempts: Int)

    /** Marks the row for [code] withdrawn by its author; an unknown code is a no-op. */
    suspend fun markUnpublished(code: ShareCode)
}
