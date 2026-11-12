package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The author's published puzzles, one row per share code. Readers take the visible `owner` (null = no
 * filter) and return that owner's rows plus ownerless ones.
 */
@Dao
interface PublishedPuzzleDao {

    /** Every published puzzle visible to [owner], newest createdAt first. */
    @Query(
        "SELECT * FROM published_puzzles WHERE (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner) " +
            "ORDER BY createdAt DESC",
    )
    fun observeAll(owner: String?): Flow<List<PublishedPuzzleEntity>>

    @Query(
        "SELECT * FROM published_puzzles WHERE code = :code " +
            "AND (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)",
    )
    fun observe(code: String, owner: String?): Flow<PublishedPuzzleEntity?>

    @Upsert
    suspend fun upsert(row: PublishedPuzzleEntity)

    @Query("UPDATE published_puzzles SET unpublished = 1 WHERE code = :code")
    suspend fun markUnpublished(code: String)

    @Query("UPDATE published_puzzles SET solvesCount = :count, solvesCountAt = :at WHERE code = :code")
    suspend fun updateSolves(code: String, count: Int, at: Long)
}
