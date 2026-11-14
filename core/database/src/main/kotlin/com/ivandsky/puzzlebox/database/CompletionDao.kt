package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The rows a reader sees for owner `:owner` (ADR-0029): its own plus ownerless ones; a null owner means no filter, so
 * every query returns what it did before accounts.
 */
internal const val VISIBLE = "(:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)"

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

    // Cloud sync (ADR-0029). The queries below are keyed by account or sync id, not by what a screen sees.

    /** Inserts [completion] unless its sync id is taken; returns the new row id, or -1 when it was ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(completion: CompletionEntity): Long

    /** Which of [syncIds] already name a row, whoever owns it. */
    @Query("SELECT syncId FROM completions WHERE syncId IN (:syncIds)")
    suspend fun existingSyncIds(syncIds: List<String>): List<String>

    /** [uid]'s rows the cloud has not acknowledged, oldest finish first. */
    @Query("SELECT * FROM completions WHERE ownerUid = :uid AND uploaded = 0 ORDER BY completedAtMs, syncId")
    suspend fun pending(uid: String): List<CompletionEntity>

    /** As [pending], re-emitting when the table changes. */
    @Query("SELECT * FROM completions WHERE ownerUid = :uid AND uploaded = 0 ORDER BY completedAtMs, syncId")
    fun observePending(uid: String): Flow<List<CompletionEntity>>

    @Query("UPDATE completions SET uploaded = 1 WHERE ownerUid = :uid AND syncId IN (:syncIds)")
    suspend fun markUploaded(uid: String, syncIds: List<String>)

    /** Makes every row of [uid] pending again, for a re-upload after the cloud space was reset. */
    @Query("UPDATE completions SET uploaded = 0 WHERE ownerUid = :uid")
    suspend fun clearUploaded(uid: String)

    /** The puzzle refs of the rows nobody owns yet. */
    @Query("SELECT DISTINCT puzzleRef FROM completions WHERE ownerUid IS NULL")
    suspend fun ownerlessRefs(): List<String>

    /** Gives every ownerless row to [uid]. */
    @Query("UPDATE completions SET ownerUid = :uid WHERE ownerUid IS NULL")
    suspend fun claimOwnerless(uid: String)

    /** Every row of [refs] that [owner] sees, for the record recompute. */
    @Query("SELECT * FROM completions WHERE puzzleRef IN (:refs) AND $VISIBLE")
    suspend fun rowsOfRefs(refs: List<String>, owner: String?): List<CompletionEntity>

    @Update
    suspend fun update(rows: List<CompletionEntity>)
}
