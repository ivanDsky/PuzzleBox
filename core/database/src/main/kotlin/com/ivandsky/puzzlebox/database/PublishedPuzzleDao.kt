package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The author's published puzzles, one row per share code. */
@Dao
interface PublishedPuzzleDao {

    /** Every published puzzle, newest createdAt first. */
    @Query("SELECT * FROM published_puzzles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PublishedPuzzleEntity>>

    @Query("SELECT * FROM published_puzzles WHERE code = :code")
    fun observe(code: String): Flow<PublishedPuzzleEntity?>

    @Upsert
    suspend fun upsert(row: PublishedPuzzleEntity)

    @Query("UPDATE published_puzzles SET unpublished = 1 WHERE code = :code")
    suspend fun markUnpublished(code: String)

    @Query("UPDATE published_puzzles SET solvesCount = :count, solvesCountAt = :at WHERE code = :code")
    suspend fun updateSolves(code: String, count: Int, at: Long)
}
