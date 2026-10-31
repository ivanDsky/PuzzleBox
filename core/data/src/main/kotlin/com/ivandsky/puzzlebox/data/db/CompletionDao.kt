package com.ivandsky.puzzlebox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Records + replays store (A4/A5): one isRecord row per puzzleRef; replays never purge it. */
@Dao
interface CompletionDao {

    /** The ref's record row, or null before the first in-app completion. */
    @Query("SELECT * FROM completions WHERE puzzleRef = :puzzleRef AND isRecord = 1 LIMIT 1")
    suspend fun record(puzzleRef: String): CompletionEntity?

    @Insert
    suspend fun insert(completion: CompletionEntity): Long

    /** Record rows only (pinned contract: observeRecords) — newest first. */
    @Query("SELECT * FROM completions WHERE isRecord = 1 ORDER BY firstCompletedAtMs DESC")
    fun observeRecords(): Flow<List<CompletionEntity>>

    /** Replay rows of one ref, oldest first (stats only; never leaderboard-eligible, A5). */
    @Query(
        "SELECT * FROM completions " +
            "WHERE puzzleRef = :puzzleRef AND isRecord = 0 ORDER BY completedAtMs ASC",
    )
    fun observeReplays(puzzleRef: String): Flow<List<CompletionEntity>>
}
