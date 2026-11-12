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

    // Cloud sync (schema v11). `uploadedFingerprint` holds the post state last uploaded: every other synced field is
    // written by `upsert`, which clears it, so a row is pending when it is null or differs from `postState`.

    /** The row for [code] whoever owns it, for the sync merge. */
    @Query("SELECT * FROM received_puzzles WHERE code = :code")
    suspend fun find(code: String): ReceivedPuzzleEntity?

    /** Claims the ownerless rows for [uid]. */
    @Query("UPDATE received_puzzles SET ownerUid = :uid WHERE ownerUid IS NULL")
    suspend fun adopt(uid: String)

    /** [uid]'s rows not yet uploaded in their current state, oldest first. */
    @Query(
        "SELECT * FROM received_puzzles WHERE ownerUid = :uid " +
            "AND (uploadedFingerprint IS NULL OR uploadedFingerprint != postState) ORDER BY openedAt, code",
    )
    suspend fun pendingUpload(uid: String): List<ReceivedPuzzleEntity>

    /** The codes of [uid]'s rows not yet uploaded and of its received deletes not yet uploaded. */
    @Query(
        "SELECT code FROM received_puzzles WHERE ownerUid = :uid " +
            "AND (uploadedFingerprint IS NULL OR uploadedFingerprint != postState) " +
            "UNION SELECT docId FROM sync_tombstones WHERE kind = 'received' AND ownerUid = :uid AND uploaded = 0",
    )
    fun observePendingCodes(uid: String): Flow<List<String>>

    /** Records that [state] of [code] reached the cloud; a row replaced since (another [openedAt]) stays pending. */
    @Query(
        "UPDATE received_puzzles SET uploadedFingerprint = :state " +
            "WHERE code = :code AND ownerUid = :uid AND openedAt = :openedAt",
    )
    suspend fun markUploaded(code: String, uid: String, openedAt: Long, state: String)

    /** Makes every row of [uid] pending again. */
    @Query("UPDATE received_puzzles SET uploadedFingerprint = NULL WHERE ownerUid = :uid")
    suspend fun clearUploaded(uid: String)

    /** When [uid] deleted [code] on this device, or null when it has no tombstone for it. */
    @Query("SELECT deletedAtMs FROM sync_tombstones WHERE kind = 'received' AND docId = :code AND ownerUid = :uid")
    suspend fun tombstonedAt(code: String, uid: String): Long?

    /** Drops [uid]'s received tombstones whose code has a row again: the puzzle was opened after the delete. */
    @Query(
        "DELETE FROM sync_tombstones WHERE kind = 'received' AND ownerUid = :uid " +
            "AND docId IN (SELECT code FROM received_puzzles WHERE ownerUid = :uid)",
    )
    suspend fun dropReopenedTombstones(uid: String)

    /** Makes every received tombstone of [uid] pending again. */
    @Query("UPDATE sync_tombstones SET uploaded = 0 WHERE kind = 'received' AND ownerUid = :uid")
    suspend fun clearTombstonesUploaded(uid: String)
}
