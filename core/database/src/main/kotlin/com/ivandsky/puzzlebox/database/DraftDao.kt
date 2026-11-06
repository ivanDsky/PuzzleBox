package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Editor drafts (A6), one row per draft id. */
@Dao
interface DraftDao {

    /** Every draft, newest updatedAt first. */
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    fun observe(id: String): Flow<DraftEntity?>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun get(id: String): DraftEntity?

    @Upsert
    suspend fun upsert(row: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)
}
