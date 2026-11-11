package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** This device's memory of duels, one row per match code. */
@Dao
interface MatchLocalDao {

    /** Every row, dismissed ones included, newest openedAt first. */
    @Query("SELECT * FROM matches_local ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<MatchLocalEntity>>

    /** Rows that carry an opponent name. */
    @Query("SELECT * FROM matches_local WHERE opponentName IS NOT NULL")
    fun observeNamed(): Flow<List<MatchLocalEntity>>

    /** Inserts a first-seen row; an existing row is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: MatchLocalEntity)

    /** Role never downgrades from Participant; a null [name] keeps the stored one. */
    @Query(
        "UPDATE matches_local SET " +
            "role = CASE WHEN :role = 'Participant' THEN 'Participant' ELSE role END, " +
            "opponentName = COALESCE(:name, opponentName) WHERE code = :code",
    )
    suspend fun merge(code: String, role: String, name: String?)

    /** Stamps the first reveal with its scores; a row already stamped keeps both. */
    @Query(
        "UPDATE matches_local SET revealSeenAt = :at, revealMyScore = :myScore, revealTheirScore = :theirScore " +
            "WHERE code = :code AND revealSeenAt IS NULL",
    )
    suspend fun markRevealSeen(code: String, at: Long, myScore: Int, theirScore: Int)

    @Query("UPDATE matches_local SET dismissed = 1 WHERE code = :code")
    suspend fun dismiss(code: String)
}
