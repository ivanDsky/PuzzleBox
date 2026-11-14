package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Editor drafts (A6), one row per draft id. */
@Dao
interface DraftDao {

    /**
     * The drafts [owner] sees, newest updatedAt first: its own plus the ownerless ones; a null [owner] means no
     * filter (cloud sync off, or no account ever signed in here).
     */
    @Query(
        "SELECT * FROM drafts WHERE (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner) " +
            "ORDER BY updatedAt DESC",
    )
    fun observeAll(owner: String?): Flow<List<DraftEntity>>

    /** The draft [id] when [owner] sees it (same rule as [observeAll]). */
    @Query("SELECT * FROM drafts WHERE id = :id AND (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)")
    fun observe(id: String, owner: String?): Flow<DraftEntity?>

    /** The draft [id] when [owner] sees it (same rule as [observeAll]). */
    @Query("SELECT * FROM drafts WHERE id = :id AND (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)")
    suspend fun get(id: String, owner: String?): DraftEntity?

    /** The raw row of [id], whoever owns it: the write paths read the owner and upload marker they must keep. */
    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun find(id: String): DraftEntity?

    @Upsert
    suspend fun upsert(row: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)

    /** The rows [owner] owns, for the sync kind's upload pass. */
    @Query("SELECT * FROM drafts WHERE ownerUid = :owner")
    suspend fun ownedBy(owner: String): List<DraftEntity>

    /** [ownedBy] as a flow: the trigger source of the sync kind. */
    @Query("SELECT * FROM drafts WHERE ownerUid = :owner")
    fun observeOwnedBy(owner: String): Flow<List<DraftEntity>>

    /** How many of [owner]'s drafts have a blob of more than [maxBytes] bytes: the ones the sync kind never uploads. */
    @Query("SELECT COUNT(*) FROM drafts WHERE ownerUid = :owner AND length(blob) > :maxBytes")
    fun observeOversizedCount(owner: String, maxBytes: Int): Flow<Int>

    /**
     * Ids of [owner]'s draft tombstones not yet uploaded, due or not. A change here, like a change to `drafts`,
     * wakes the sync kind; it never reads the rows' content.
     */
    @Query("SELECT docId FROM sync_tombstones WHERE kind = 'drafts' AND ownerUid = :owner AND uploaded = 0")
    fun observeUnsentTombstoneIds(owner: String): Flow<List<String>>

    /** Claims every ownerless draft for [owner]. */
    @Query("UPDATE drafts SET ownerUid = :owner WHERE ownerUid IS NULL")
    suspend fun adopt(owner: String)

    /** Records the version of [id] the cloud holds; null makes it pending again. */
    @Query("UPDATE drafts SET uploadedFingerprint = :fingerprint WHERE id = :id")
    suspend fun setUploadedFingerprint(id: String, fingerprint: String?)

    /** Makes every draft of [owner] pending again, for a re-upload after the cloud space was reset. */
    @Query("UPDATE drafts SET uploadedFingerprint = NULL WHERE ownerUid = :owner")
    suspend fun clearUploadedFingerprints(owner: String)
}
