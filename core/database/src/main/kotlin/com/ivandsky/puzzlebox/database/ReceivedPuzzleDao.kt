package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Friends' puzzles opened on this device, one row per share code. Readers take the visible `owner`
 * (null = no filter) and return that owner's rows plus ownerless ones.
 */
@Dao
interface ReceivedPuzzleDao {

    /** Every received puzzle visible to [owner], newest openedAt first. */
    @Query(
        "SELECT * FROM received_puzzles WHERE (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner) " +
            "ORDER BY openedAt DESC",
    )
    fun observeAll(owner: String?): Flow<List<ReceivedPuzzleEntity>>

    @Query(
        "SELECT * FROM received_puzzles WHERE code = :code " +
            "AND (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)",
    )
    fun observe(code: String, owner: String?): Flow<ReceivedPuzzleEntity?>

    @Query(
        "SELECT * FROM received_puzzles WHERE code = :code " +
            "AND (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)",
    )
    suspend fun get(code: String, owner: String?): ReceivedPuzzleEntity?

    @Upsert
    suspend fun upsert(row: ReceivedPuzzleEntity)

    /**
     * Removes the row for [code]; an owned row also leaves a `received` tombstone for its owner, eligible
     * at once, in the same transaction. An ownerless or unknown row leaves none.
     */
    @Transaction
    suspend fun deleteWithTombstone(code: String, nowMs: Long) {
        insertTombstoneOfOwned(code, nowMs)
        delete(code)
    }

    @Query(
        "INSERT OR REPLACE INTO sync_tombstones (kind, docId, ownerUid, deletedAtMs, eligibleAtMs, uploaded) " +
            "SELECT 'received', code, ownerUid, :nowMs, :nowMs, 0 FROM received_puzzles " +
            "WHERE code = :code AND ownerUid IS NOT NULL",
    )
    suspend fun insertTombstoneOfOwned(code: String, nowMs: Long)

    @Query("DELETE FROM received_puzzles WHERE code = :code")
    suspend fun delete(code: String)

    @Query("UPDATE received_puzzles SET postState = :state, postAttempts = :attempts WHERE code = :code")
    suspend fun updatePost(code: String, state: String, attempts: Int)

    @Query("UPDATE received_puzzles SET unpublished = 1 WHERE code = :code")
    suspend fun markUnpublished(code: String)
}
