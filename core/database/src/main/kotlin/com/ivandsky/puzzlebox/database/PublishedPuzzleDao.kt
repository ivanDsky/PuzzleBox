package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The author's published puzzles, one row per share code. Readers take the visible `owner` (null = no
 * filter) and return that owner's rows plus ownerless ones.
 */
@Dao
interface PublishedPuzzleDao {

    /** Every published puzzle visible to [owner], newest createdAt first. */
    @Query(
        "SELECT * FROM published_puzzles WHERE (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner) " +
            "ORDER BY createdAt DESC",
    )
    fun observeAll(owner: String?): Flow<List<PublishedPuzzleEntity>>

    @Query(
        "SELECT * FROM published_puzzles WHERE code = :code " +
            "AND (:owner IS NULL OR ownerUid IS NULL OR ownerUid = :owner)",
    )
    fun observe(code: String, owner: String?): Flow<PublishedPuzzleEntity?>

    @Upsert
    suspend fun upsert(row: PublishedPuzzleEntity)

    @Query("UPDATE published_puzzles SET unpublished = 1 WHERE code = :code")
    suspend fun markUnpublished(code: String)

    @Query("UPDATE published_puzzles SET solvesCount = :count, solvesCountAt = :at WHERE code = :code")
    suspend fun updateSolves(code: String, count: Int, at: Long)

    /** The stored row for [code] whatever its owner, or null; the sync merge reads it. */
    @Query("SELECT * FROM published_puzzles WHERE code = :code")
    suspend fun find(code: String): PublishedPuzzleEntity?

    /** Every row owned by [uid], for the sync upload. */
    @Query("SELECT * FROM published_puzzles WHERE ownerUid = :uid ORDER BY createdAt, code")
    suspend fun owned(uid: String): List<PublishedPuzzleEntity>

    /** The rows owned by [uid], re-emitted on every write to the table; sync derives its pending ids from it. */
    @Query("SELECT * FROM published_puzzles WHERE ownerUid = :uid")
    fun observeOwned(uid: String): Flow<List<PublishedPuzzleEntity>>

    /** Gives the ownerless rows to [uid]. */
    @Query("UPDATE published_puzzles SET ownerUid = :uid WHERE ownerUid IS NULL")
    suspend fun adopt(uid: String)

    /** Records [fingerprint] as the last uploaded version of [uid]'s row [code]. */
    @Query("UPDATE published_puzzles SET uploadedFingerprint = :fingerprint WHERE code = :code AND ownerUid = :uid")
    suspend fun setFingerprint(code: String, uid: String, fingerprint: String)

    /** Clears the upload marker of every row [uid] owns, so all of them upload again. */
    @Query("UPDATE published_puzzles SET uploadedFingerprint = NULL WHERE ownerUid = :uid")
    suspend fun clearFingerprints(uid: String)
}
