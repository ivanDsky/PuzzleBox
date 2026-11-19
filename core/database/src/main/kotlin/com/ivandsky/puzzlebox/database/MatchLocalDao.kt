package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A reveal still to upload: seen under the account, not yet in the cloud, and not a self duel's (those stay on the device). */
private const val PENDING_REVEAL = "ownerUid = :uid AND revealSeenAt IS NOT NULL AND revealUploaded = 0 " +
    "AND code NOT IN (SELECT code FROM self_matches)"

/** This device's memory of duels, one row per match code, and which seen reveals are in the account's cloud (#1542). */
@Dao
interface MatchLocalDao {

    /** Every row, dismissed ones included, newest openedAt first. */
    @Query("SELECT * FROM matches_local ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<MatchLocalEntity>>

    /** Rows that carry an opponent name. */
    @Query("SELECT * FROM matches_local WHERE opponentName IS NOT NULL")
    fun observeNamed(): Flow<List<MatchLocalEntity>>

    /** Inserts a first-seen row; an existing row is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: MatchLocalEntity)

    /** Role never downgrades from Participant; a null [name] keeps the stored one. */
    @Query(
        "UPDATE matches_local SET " +
            "role = CASE WHEN :role = 'Participant' THEN 'Participant' ELSE role END, " +
            "opponentName = COALESCE(:name, opponentName) WHERE code = :code",
    )
    suspend fun merge(code: String, role: String, name: String?)

    /**
     * Stamps the first reveal with its scores and the account it was seen under ([owner], null when signed out); a row
     * already stamped keeps all of them. Returns how many rows it stamped: 1 or 0.
     */
    @Query(
        "UPDATE matches_local SET revealSeenAt = :at, revealMyScore = :myScore, revealTheirScore = :theirScore, " +
            "ownerUid = :owner WHERE code = :code AND revealSeenAt IS NULL",
    )
    suspend fun markRevealSeen(code: String, at: Long, myScore: Int?, theirScore: Int?, owner: String?): Int

    /** The codes of [uid]'s seen reveals not yet in the cloud; a self duel's reveal never is. */
    @Query("SELECT code FROM matches_local WHERE $PENDING_REVEAL")
    fun observePendingReveals(uid: String): Flow<List<String>>

    /** [uid]'s seen reveals not yet in the cloud, as [observePendingReveals] names them. */
    @Query("SELECT * FROM matches_local WHERE $PENDING_REVEAL")
    suspend fun pendingReveals(uid: String): List<MatchLocalEntity>

    /** Gives [uid] the reveals seen while nobody was signed in. */
    @Query("UPDATE matches_local SET ownerUid = :uid WHERE ownerUid IS NULL AND revealSeenAt IS NOT NULL")
    suspend fun claimOwnerlessReveals(uid: String)

    /** Marks [uid]'s reveals of [codes] as in the cloud. */
    @Query("UPDATE matches_local SET revealUploaded = 1 WHERE ownerUid = :uid AND code IN (:codes)")
    suspend fun markRevealsUploaded(uid: String, codes: List<String>)

    /**
     * Marks the reveal of [code] as in [uid]'s cloud, claiming it when it has no owner: the cloud already holds the
     * account's first reveal of this duel, so this device never uploads its own. Another account's reveal is untouched.
     */
    @Query(
        "UPDATE matches_local SET ownerUid = :uid, revealUploaded = 1 " +
            "WHERE code = :code AND revealSeenAt IS NOT NULL AND (ownerUid IS NULL OR ownerUid = :uid)",
    )
    suspend fun markRevealInCloud(uid: String, code: String)

    /** Clears [uid]'s upload markers, so every reveal of the account goes up again. */
    @Query("UPDATE matches_local SET revealUploaded = 0 WHERE ownerUid = :uid")
    suspend fun clearRevealsUploaded(uid: String)

    @Query("UPDATE matches_local SET dismissed = 1 WHERE code = :code")
    suspend fun dismiss(code: String)

    @Query("DELETE FROM matches_local WHERE code = :code")
    suspend fun delete(code: String)
}
