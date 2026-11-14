package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Replays of completions by puzzle; a puzzle keeps only its record solve's and latest solve's per visible owner after [prune]. */
@Dao
interface CompletionReplayDao {

    /** Insert the completion row first, in the same transaction: the replay row references it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: CompletionReplayEntity)

    /** Replay of the puzzle's record row (isRecord = 1) among the rows [owner] sees; null when it has none. */
    @Query(
        "SELECT r.replay FROM completion_replays r JOIN completions c ON c.id = r.completionId " +
            "WHERE c.puzzleRef = :refKey AND c.isRecord = 1 AND $VISIBLE LIMIT 1",
    )
    suspend fun recordReplay(refKey: String, owner: String?): ByteArray?

    /** Replay of the newest row by completedAtMs among the rows [owner] sees; null when it has none. */
    @Query(
        "SELECT r.replay FROM completion_replays r WHERE r.completionId = " +
            "(SELECT id FROM completions WHERE puzzleRef = :refKey AND $VISIBLE ORDER BY completedAtMs DESC, id DESC LIMIT 1)",
    )
    suspend fun latestReplay(refKey: String, owner: String?): ByteArray?

    /** True when the newest row [owner] sees is the record row, or the puzzle has no such row. */
    @Query(
        "SELECT COALESCE((SELECT isRecord FROM completions WHERE puzzleRef = :refKey AND $VISIBLE " +
            "ORDER BY completedAtMs DESC, id DESC LIMIT 1), 1)",
    )
    suspend fun latestIsRecord(refKey: String, owner: String?): Boolean

    /** Deletes the replay rows [owner] sees that belong to neither the puzzle's record row nor its newest row. */
    @Query(
        "DELETE FROM completion_replays WHERE completionId IN " +
            "(SELECT id FROM completions WHERE puzzleRef = :refKey AND isRecord = 0 AND $VISIBLE " +
            "AND id <> COALESCE((SELECT id FROM completions WHERE puzzleRef = :refKey AND $VISIBLE " +
            "ORDER BY completedAtMs DESC, id DESC LIMIT 1), -1))",
    )
    suspend fun prune(refKey: String, owner: String?)

    /** Cloud ids of [uid]'s completions whose replay is not uploaded, re-emitting when either table changes. */
    @Query(
        "SELECT c.syncId FROM completion_replays r JOIN completions c ON c.id = r.completionId " +
            "WHERE c.ownerUid = :uid AND r.uploaded = 0 ORDER BY c.completedAtMs, c.syncId",
    )
    fun observePendingSyncIds(uid: String): Flow<List<String>>

    /** Replays of [uid]'s completions not yet uploaded, oldest completion first. */
    @Query(
        "SELECT c.syncId AS syncId, r.replay AS replay FROM completion_replays r JOIN completions c ON c.id = r.completionId " +
            "WHERE c.ownerUid = :uid AND r.uploaded = 0 ORDER BY c.completedAtMs, c.syncId",
    )
    suspend fun pending(uid: String): List<CompletionReplayCloudRow>

    /** Marks the replays of [uid]'s completions [syncIds] as stored in the cloud. */
    @Query(
        "UPDATE completion_replays SET uploaded = 1 WHERE completionId IN " +
            "(SELECT id FROM completions WHERE ownerUid = :uid AND syncId IN (:syncIds))",
    )
    suspend fun markUploaded(uid: String, syncIds: List<String>)

    /** Makes every replay of [uid]'s completions pending again, for a re-upload after the cloud space was reset. */
    @Query(
        "UPDATE completion_replays SET uploaded = 0 WHERE completionId IN " +
            "(SELECT id FROM completions WHERE ownerUid = :uid)",
    )
    suspend fun clearUploaded(uid: String)

    /**
     * The id of [uid]'s completion [syncId] when it is the record row or the newest row of its puzzle, i.e. the rows
     * [prune] keeps a replay for; null for any other row, an unknown id or another owner's row.
     */
    @Query(
        "SELECT c.id FROM completions c WHERE c.ownerUid = :uid AND c.syncId = :syncId AND (c.isRecord = 1 " +
            "OR c.id = (SELECT id FROM completions WHERE puzzleRef = c.puzzleRef ORDER BY completedAtMs DESC, id DESC LIMIT 1))",
    )
    suspend fun keptCompletionId(uid: String, syncId: String): Long?

    /** Inserts a restored replay (`uploaded = true`) and leaves a replay the completion already has untouched; -1 when ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: CompletionReplayEntity): Long
}
