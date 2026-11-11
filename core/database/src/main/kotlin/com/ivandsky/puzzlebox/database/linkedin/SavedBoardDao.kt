package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Saved LinkedIn boards (schema v2), one row per (game, day). Their ghosts are read through [GhostDao]. */
@Dao
interface SavedBoardDao {

    /** Inserts the board or replaces the one of the same (game, day). */
    @Upsert
    suspend fun upsert(board: SavedBoardEntity)

    @Query("SELECT * FROM saved_boards WHERE game = :game AND day = :day")
    suspend fun get(game: String, day: String): SavedBoardEntity?

    @Query("SELECT * FROM saved_boards WHERE game = :game AND day = :day")
    fun observe(game: String, day: String): Flow<SavedBoardEntity?>

    /** Every day of [game] that has a saved board, oldest first, re-emitted on change. */
    @Query("SELECT day FROM saved_boards WHERE game = :game ORDER BY day")
    fun observeDays(game: String): Flow<List<String>>

    @Query("DELETE FROM saved_boards WHERE game = :game AND day = :day")
    suspend fun delete(game: String, day: String)

    @Query("DELETE FROM saved_boards WHERE ownerUid = :ownerUid")
    suspend fun deleteForOwner(ownerUid: String)

    /** Boards of [ownerUid] with no acknowledged upload, in (game, day) order. */
    @Query("SELECT * FROM saved_boards WHERE ownerUid = :ownerUid AND uploadedFingerprint IS NULL ORDER BY game, day")
    suspend fun pending(ownerUid: String): List<SavedBoardEntity>

    /** Records the cloud-acknowledged content of one board. */
    @Query("UPDATE saved_boards SET uploadedFingerprint = :fingerprint WHERE game = :game AND day = :day")
    suspend fun setFingerprint(game: String, day: String, fingerprint: String)

    /** Gives every unowned board to [ownerUid], at sign-in. */
    @Query("UPDATE saved_boards SET ownerUid = :ownerUid WHERE ownerUid IS NULL")
    suspend fun adoptNullOwner(ownerUid: String)
}
