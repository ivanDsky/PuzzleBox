package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Pending cloud deletes (schema v11), one row per (kind, docId, ownerUid). */
@Dao
interface SyncTombstoneDao {

    /** Inserts the tombstone, replacing any row with the same key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(row: SyncTombstoneEntity)

    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND docId = :docId AND ownerUid = :ownerUid")
    suspend fun delete(kind: String, docId: String, ownerUid: String)

    /** Tombstones of [ownerUid] not yet uploaded whose delay has passed at [nowMs], oldest delete first. */
    @Query(
        "SELECT * FROM sync_tombstones WHERE ownerUid = :ownerUid AND uploaded = 0 AND eligibleAtMs <= :nowMs " +
            "ORDER BY deletedAtMs, kind, docId",
    )
    suspend fun pendingEligible(ownerUid: String, nowMs: Long): List<SyncTombstoneEntity>

    @Query("UPDATE sync_tombstones SET uploaded = 1 WHERE kind = :kind AND docId = :docId AND ownerUid = :ownerUid")
    suspend fun markUploaded(kind: String, docId: String, ownerUid: String)

    /** Makes every tombstone of [ownerUid] pending again, for a re-upload after the cloud space was reset. */
    @Query("UPDATE sync_tombstones SET uploaded = 0 WHERE ownerUid = :ownerUid")
    suspend fun clearUploaded(ownerUid: String)
}
