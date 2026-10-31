package com.ivandsky.puzzlebox.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPuzzleDao {
    @Upsert
    suspend fun upsert(puzzle: DailyPuzzleEntity)

    @Query("SELECT * FROM daily_puzzles WHERE gameId = :gameId ORDER BY day DESC")
    fun observeByGame(gameId: String): Flow<List<DailyPuzzleEntity>>

    @Query("SELECT * FROM daily_puzzles WHERE gameId = :gameId AND day = :day")
    suspend fun get(gameId: String, day: String): DailyPuzzleEntity?

    @Query("SELECT COUNT(*) FROM daily_puzzles")
    suspend fun count(): Int

    @Query("DELETE FROM daily_puzzles")
    suspend fun clear()
}
