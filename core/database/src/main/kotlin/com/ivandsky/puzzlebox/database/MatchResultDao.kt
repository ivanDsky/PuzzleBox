package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The local queue of duel results awaiting a post, one row per (match, leg, player). */
@Dao
interface MatchResultDao {

    /** Inserts a first-seen row; an existing row is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: MatchResultEntity)

    /** Sets post state and attempts of an existing row; no-op when it is missing. */
    @Query(
        "UPDATE match_results SET postState = :state, postAttempts = :attempts " +
            "WHERE matchCode = :code AND legIndex = :legIndex AND uid = :uid",
    )
    suspend fun update(code: String, legIndex: Int, uid: String, state: String, attempts: Int)

    @Query("SELECT * FROM match_results WHERE matchCode = :code ORDER BY legIndex, uid")
    fun observe(code: String): Flow<List<MatchResultEntity>>

    @Query("SELECT * FROM match_results WHERE postState = 'Pending' ORDER BY completedAtMs, matchCode, legIndex, uid")
    fun observePending(): Flow<List<MatchResultEntity>>

    @Query("SELECT * FROM match_results ORDER BY completedAtMs DESC, matchCode, legIndex, uid")
    fun observeAll(): Flow<List<MatchResultEntity>>
}
