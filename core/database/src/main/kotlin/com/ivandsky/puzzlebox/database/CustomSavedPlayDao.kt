package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Saved in-progress plays of friends' puzzles (A13): one slot per puzzleId, capped at the
 * newest `cap` across the table — apart from `saved_plays`, so a friend save never evicts a daily.
 */
@Dao
interface CustomSavedPlayDao {

    /** Upserts [row], then keeps only the newest [cap] slots by updatedAt, atomically. */
    @Transaction
    suspend fun saveAndPrune(row: CustomSavedPlayEntity, cap: Int) {
        upsert(row)
        pruneTo(cap)
    }

    /** The saved session blob of one friend's puzzle, or null when no slot exists. */
    @Query("SELECT blob FROM custom_saved_plays WHERE puzzleId = :puzzleId")
    suspend fun blob(puzzleId: String): ByteArray?

    @Query("DELETE FROM custom_saved_plays WHERE puzzleId = :puzzleId")
    suspend fun delete(puzzleId: String)

    /** Every slot, newest first, without blobs. */
    @Query("SELECT puzzleId, gameId, elapsedMs FROM custom_saved_plays ORDER BY updatedAt DESC")
    fun observeSlots(): Flow<List<CustomSavedPlaySlotRow>>

    @Upsert
    suspend fun upsert(row: CustomSavedPlayEntity)

    @Query(
        "DELETE FROM custom_saved_plays WHERE puzzleId NOT IN " +
            "(SELECT puzzleId FROM custom_saved_plays ORDER BY updatedAt DESC LIMIT :cap)",
    )
    suspend fun pruneTo(cap: Int)
}
