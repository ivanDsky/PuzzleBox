package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Saved in-progress plays of duel puzzles: one slot per (matchCode, legIndex), capped at
 * the newest `cap` across the table — apart from `saved_plays` and `custom_saved_plays`, so a
 * leg save never evicts a daily or friend save, nor they a leg.
 */
@Dao
interface LegSavedPlayDao {

    /** Upserts [row], then keeps only the newest [cap] slots by updatedAt, atomically. */
    @Transaction
    suspend fun saveAndPrune(row: LegSavedPlayEntity, cap: Int) {
        upsert(row)
        pruneTo(cap)
    }

    /** The saved session blob of one leg of one match, or null when no slot exists. */
    @Query("SELECT blob FROM leg_saved_plays WHERE matchCode = :matchCode AND legIndex = :legIndex")
    suspend fun blob(matchCode: String, legIndex: Int): ByteArray?

    @Query("DELETE FROM leg_saved_plays WHERE matchCode = :matchCode AND legIndex = :legIndex")
    suspend fun delete(matchCode: String, legIndex: Int)

    /** Every slot, newest first, without blobs. */
    @Query("SELECT matchCode, legIndex, gameId, elapsedMs FROM leg_saved_plays ORDER BY updatedAt DESC")
    fun observeSlots(): Flow<List<LegSavedPlaySlotRow>>

    @Upsert
    suspend fun upsert(row: LegSavedPlayEntity)

    @Query(
        "DELETE FROM leg_saved_plays WHERE rowid NOT IN " +
            "(SELECT rowid FROM leg_saved_plays ORDER BY updatedAt DESC LIMIT :cap)",
    )
    suspend fun pruneTo(cap: Int)
}
