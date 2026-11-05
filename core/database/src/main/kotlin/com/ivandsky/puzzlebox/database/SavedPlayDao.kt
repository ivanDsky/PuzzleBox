package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Saved in-progress plays (A10): one slot per (gameId, day), capped at the newest `cap`. */
@Dao
interface SavedPlayDao {

    /** Upserts [row], then keeps only the newest [cap] slots by updatedAt (SC8), atomically. */
    @Transaction
    suspend fun saveAndPrune(row: SavedPlayEntity, cap: Int) {
        upsert(row)
        pruneTo(cap)
    }

    /** The saved session blob of one daily, or null when no slot exists. */
    @Query("SELECT blob FROM saved_plays WHERE gameId = :gameId AND day = :day")
    suspend fun blob(gameId: String, day: String): ByteArray?

    @Query("DELETE FROM saved_plays WHERE gameId = :gameId AND day = :day")
    suspend fun delete(gameId: String, day: String)

    /** Every slot, newest first, without blobs. */
    @Query("SELECT gameId, day, elapsedMs FROM saved_plays ORDER BY updatedAt DESC")
    fun observeSlots(): Flow<List<SavedPlaySlotRow>>

    @Upsert
    suspend fun upsert(row: SavedPlayEntity)

    @Query(
        "DELETE FROM saved_plays WHERE rowid NOT IN " +
            "(SELECT rowid FROM saved_plays ORDER BY updatedAt DESC LIMIT :cap)",
    )
    suspend fun pruneTo(cap: Int)
}
