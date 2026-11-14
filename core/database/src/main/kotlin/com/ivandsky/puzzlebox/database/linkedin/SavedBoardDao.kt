package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Saved LinkedIn boards (schema v2), one row per (game, day). Their ghosts are read through [GhostDao]. */
@Dao
interface SavedBoardDao {

    /** Inserts the board or replaces the one of the same (game, day). */
    @Upsert
    suspend fun upsert(board: SavedBoardEntity)

    @Query("SELECT * FROM saved_boards WHERE game = :game AND day = :day")
    suspend fun get(game: String, day: String): SavedBoardEntity?

    /** The saved boards of one game from [from] to [to], both included. */
    @Query("SELECT * FROM saved_boards WHERE game = :game AND day BETWEEN :from AND :to")
    suspend fun between(game: String, from: String, to: String): List<SavedBoardEntity>

    /** Every saved board of every owner, in (game, day) order. */
    @Query("SELECT * FROM saved_boards ORDER BY game, day")
    suspend fun all(): List<SavedBoardEntity>

    @Query("DELETE FROM saved_boards WHERE game = :game AND day = :day")
    suspend fun delete(game: String, day: String)

    /** Boards of [ownerUid] with no acknowledged upload, in (game, day) order. */
    @Query("SELECT * FROM saved_boards WHERE ownerUid = :ownerUid AND uploadedFingerprint IS NULL ORDER BY game, day")
    suspend fun pending(ownerUid: String): List<SavedBoardEntity>

    /** Records the cloud-acknowledged content of one board. */
    @Query("UPDATE saved_boards SET uploadedFingerprint = :fingerprint WHERE game = :game AND day = :day")
    suspend fun setFingerprint(game: String, day: String, fingerprint: String)

    /** Forgets what the cloud holds of [ownerUid]'s boards, so every one of them is pending again. */
    @Query("UPDATE saved_boards SET uploadedFingerprint = NULL WHERE ownerUid = :ownerUid")
    suspend fun clearFingerprints(ownerUid: String)

    /** Gives every unowned board to [ownerUid], at sign-in. */
    @Query("UPDATE saved_boards SET ownerUid = :ownerUid WHERE ownerUid IS NULL")
    suspend fun adoptNullOwner(ownerUid: String)
}
