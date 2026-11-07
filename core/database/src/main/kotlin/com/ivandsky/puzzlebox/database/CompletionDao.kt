package com.ivandsky.puzzlebox.database

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

    /** Record and replay rows of one ref, newest first. */
    @Query("SELECT * FROM completions WHERE puzzleRef = :refKey ORDER BY completedAtMs DESC")
    fun observeByRef(refKey: String): Flow<List<CompletionEntity>>

    /**
     * The best record row (first solves; replays never rank, A10) whose key starts with
     * [prefix] — a game's rows by `"$gameId/"` — time first, hints breaking ties; null when
     * none. `substr`, not LIKE: a `_` in a game id would be a wildcard, and the trailing
     * slash keeps `zip`/`zip2` apart.
     */
    @Query(
        "SELECT * FROM completions " +
            "WHERE isRecord = 1 AND substr(puzzleRef, 1, length(:prefix)) = :prefix " +
            "ORDER BY timeMs, hints LIMIT 1",
    )
    suspend fun bestByTime(prefix: String): CompletionEntity?

    /** As [bestByTime], fewest hints first, time breaking ties (ADR-0013). */
    @Query(
        "SELECT * FROM completions " +
            "WHERE isRecord = 1 AND substr(puzzleRef, 1, length(:prefix)) = :prefix " +
            "ORDER BY hints, timeMs LIMIT 1",
    )
    suspend fun bestByHints(prefix: String): CompletionEntity?
}
