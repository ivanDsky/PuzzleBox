package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The kept weeks of the player's Friends rank: read by owner, game and week range, written once per week. */
@Dao
interface RankHistoryDao {

    /**
     * Keeps [week] unless its (ownerUid, gameId, weekStart) is already kept; a kept week is never rewritten.
     * Returns the new row id, or -1 when a kept week made this a no-op.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnce(week: RankHistoryEntity): Long

    /** The weeks of [ownerUid] and [gameId] whose ISO start date is in [from]..[to], both inclusive, oldest first. */
    @Query(
        "SELECT * FROM rank_history WHERE ownerUid = :ownerUid AND gameId = :gameId " +
            "AND weekStart BETWEEN :from AND :to ORDER BY weekStart",
    )
    suspend fun weeks(ownerUid: String, gameId: String, from: String, to: String): List<RankHistoryEntity>
}
