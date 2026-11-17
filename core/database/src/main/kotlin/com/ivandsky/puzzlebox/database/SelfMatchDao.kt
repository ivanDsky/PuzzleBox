package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The self-duel store: one [SelfMatchEntity] per code and its frozen [SelfPastLegEntity] rows. */
@Dao
interface SelfMatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: SelfMatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPastLegs(legs: List<SelfPastLegEntity>)

    /** Writes the match and its past legs together, so a reader never sees one without the other. */
    @Transaction
    suspend fun insert(match: SelfMatchEntity, pastLegs: List<SelfPastLegEntity>) {
        insertMatch(match)
        insertPastLegs(pastLegs)
    }

    @Query("SELECT * FROM self_matches WHERE code = :code")
    fun observeMatch(code: String): Flow<SelfMatchEntity?>

    @Query("SELECT * FROM self_past_legs WHERE code = :code ORDER BY legIndex")
    fun observePastLegs(code: String): Flow<List<SelfPastLegEntity>>

    @Query("SELECT * FROM self_matches WHERE code = :code")
    suspend fun match(code: String): SelfMatchEntity?

    @Query("SELECT * FROM self_past_legs WHERE code = :code ORDER BY legIndex")
    suspend fun pastLegs(code: String): List<SelfPastLegEntity>

    /** Every self duel, newest first. */
    @Query("SELECT * FROM self_matches ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<SelfMatchEntity>>

    @Query("DELETE FROM self_matches WHERE code = :code")
    suspend fun deleteMatch(code: String)

    @Query("DELETE FROM self_past_legs WHERE code = :code")
    suspend fun deletePastLegs(code: String)

    /** Removes the match and its past legs together. */
    @Transaction
    suspend fun delete(code: String) {
        deletePastLegs(code)
        deleteMatch(code)
    }
}
