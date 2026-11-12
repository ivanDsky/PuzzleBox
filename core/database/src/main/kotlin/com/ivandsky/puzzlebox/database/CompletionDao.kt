package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The rows a reader sees for owner `:owner` (ADR-0029): its own plus ownerless ones; a null owner means no filter, so
 * every query returns what it did before accounts.
 */
private const val VISIBLE = "(:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)"

/**
 * Records + play-agains store (A4/A5): one isRecord row per puzzleRef; play-agains never purge it. Every read takes the
 * visible owner and sees only that owner's rows plus ownerless ones.
 */
@Dao
interface CompletionDao {

    /** The ref's record row among the rows [owner] sees, or null before their first in-app completion. */
    @Query("SELECT * FROM completions WHERE puzzleRef = :puzzleRef AND isRecord = 1 AND $VISIBLE LIMIT 1")
    suspend fun record(puzzleRef: String, owner: String?): CompletionEntity?

    /** Whether any row (record or play-again) of the ref came from [source]; backs import idempotence. */
    @Query("SELECT EXISTS(SELECT 1 FROM completions WHERE puzzleRef = :puzzleRef AND source = :source AND $VISIBLE)")
    suspend fun hasSource(puzzleRef: String, source: String, owner: String?): Boolean

    /** Every row stored with [source], records and play-agains alike, newest first. */
    @Query("SELECT * FROM completions WHERE source = :source AND $VISIBLE ORDER BY completedAtMs DESC")
    fun observeBySource(source: String, owner: String?): Flow<List<CompletionEntity>>

    @Insert
    suspend fun insert(completion: CompletionEntity): Long

    /** Record rows only (pinned contract: observeRecords) — newest first. */
    @Query("SELECT * FROM completions WHERE isRecord = 1 AND $VISIBLE ORDER BY firstCompletedAtMs DESC")
    fun observeRecords(owner: String?): Flow<List<CompletionEntity>>

    /** Play-again rows of one ref, oldest first (stats only; never leaderboard-eligible, A5). */
    @Query(
        "SELECT * FROM completions " +
            "WHERE puzzleRef = :puzzleRef AND isRecord = 0 AND $VISIBLE ORDER BY completedAtMs ASC",
    )
    fun observePlayAgains(puzzleRef: String, owner: String?): Flow<List<CompletionEntity>>

    /** Record and play-again rows of one ref, newest first. */
    @Query("SELECT * FROM completions WHERE puzzleRef = :refKey AND $VISIBLE ORDER BY completedAtMs DESC")
    fun observeByRef(refKey: String, owner: String?): Flow<List<CompletionEntity>>

    /**
     * The best record row (first solves; play-agains never rank, A10) whose key starts with
     * [prefix] — a game's rows by `"$gameId/"` — time first, hints breaking ties; null when
     * none. `substr`, not LIKE: a `_` in a game id would be a wildcard, and the trailing
     * slash keeps `zip`/`zip2` apart.
     */
    @Query(
        "SELECT * FROM completions " +
            "WHERE isRecord = 1 AND substr(puzzleRef, 1, length(:prefix)) = :prefix AND $VISIBLE " +
            "ORDER BY timeMs, hints LIMIT 1",
    )
    suspend fun bestByTime(prefix: String, owner: String?): CompletionEntity?

    /** As [bestByTime], fewest hints first, time breaking ties (ADR-0013). */
    @Query(
        "SELECT * FROM completions " +
            "WHERE isRecord = 1 AND substr(puzzleRef, 1, length(:prefix)) = :prefix AND $VISIBLE " +
            "ORDER BY hints, timeMs LIMIT 1",
    )
    suspend fun bestByHints(prefix: String, owner: String?): CompletionEntity?
}
