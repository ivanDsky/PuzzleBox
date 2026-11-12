package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Replays of completions by puzzle; a puzzle keeps only its record solve's and latest solve's after [prune]. */
@Dao
interface CompletionReplayDao {

    /** Insert the completion row first, in the same transaction: the replay row references it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: CompletionReplayEntity)

    /** Replay of the puzzle's record row (isRecord = 1); null when it has none. */
    @Query(
        "SELECT r.replay FROM completion_replays r JOIN completions c ON c.id = r.completionId " +
            "WHERE c.puzzleRef = :refKey AND c.isRecord = 1 LIMIT 1",
    )
    suspend fun recordReplay(refKey: String): ByteArray?

    /** Replay of the puzzle's newest row by completedAtMs; null when it has none. */
    @Query(
        "SELECT r.replay FROM completion_replays r WHERE r.completionId = " +
            "(SELECT id FROM completions WHERE puzzleRef = :refKey ORDER BY completedAtMs DESC, id DESC LIMIT 1)",
    )
    suspend fun latestReplay(refKey: String): ByteArray?

    /** True when the puzzle's newest row is its record row, or it has no row. */
    @Query(
        "SELECT COALESCE((SELECT isRecord FROM completions WHERE puzzleRef = :refKey " +
            "ORDER BY completedAtMs DESC, id DESC LIMIT 1), 1)",
    )
    suspend fun latestIsRecord(refKey: String): Boolean

    /** Deletes the puzzle's replay rows that belong to neither its record row nor its newest row. */
    @Query(
        "DELETE FROM completion_replays WHERE completionId IN " +
            "(SELECT id FROM completions WHERE puzzleRef = :refKey AND isRecord = 0 " +
            "AND id <> COALESCE((SELECT id FROM completions WHERE puzzleRef = :refKey " +
            "ORDER BY completedAtMs DESC, id DESC LIMIT 1), -1))",
    )
    suspend fun prune(refKey: String)
}
