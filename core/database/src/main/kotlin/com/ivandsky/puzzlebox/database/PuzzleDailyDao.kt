package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Store + history reads for the daily envelope (A8: history = days the app captured). */
@Dao
interface PuzzleDailyDao {
    @Upsert
    suspend fun upsert(puzzle: PuzzleDailyEntity)

    @Query("SELECT * FROM puzzle_dailies WHERE gameId = :gameId AND day = :day")
    suspend fun get(gameId: String, day: String): PuzzleDailyEntity?

    /** History listing, newest first, without payload bytes (ADR-0012) — ticket 14 layers Paging3 over this order. */
    @Query("SELECT day, puzzleNumber, variant, fetchedAtMs FROM puzzle_dailies WHERE gameId = :gameId ORDER BY day DESC")
    fun observeHistory(gameId: String): Flow<List<PuzzleDailyHistoryRow>>

    @Query("SELECT COUNT(*) FROM puzzle_dailies WHERE gameId = :gameId")
    suspend fun count(gameId: String): Int

    @Query("DELETE FROM puzzle_dailies WHERE gameId = :gameId")
    suspend fun clear(gameId: String)
}
