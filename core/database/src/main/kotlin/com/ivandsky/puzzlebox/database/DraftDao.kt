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
}
