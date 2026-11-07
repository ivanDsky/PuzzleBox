package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Friends' puzzles opened on this device, one row per share code. */
@Dao
interface ReceivedPuzzleDao {

    /** Every received puzzle, newest openedAt first. */
    @Query("SELECT * FROM received_puzzles ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<ReceivedPuzzleEntity>>

    @Query("SELECT * FROM received_puzzles WHERE code = :code")
    fun observe(code: String): Flow<ReceivedPuzzleEntity?>

    @Query("SELECT * FROM received_puzzles WHERE code = :code")
    suspend fun get(code: String): ReceivedPuzzleEntity?

    @Upsert
    suspend fun upsert(row: ReceivedPuzzleEntity)

    @Query("DELETE FROM received_puzzles WHERE code = :code")
    suspend fun delete(code: String)

    @Query("UPDATE received_puzzles SET postState = :state, postAttempts = :attempts WHERE code = :code")
    suspend fun updatePost(code: String, state: String, attempts: Int)

    @Query("UPDATE received_puzzles SET unpublished = 1 WHERE code = :code")
    suspend fun markUnpublished(code: String)
}
