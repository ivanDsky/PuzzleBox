package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The rowid of the last row read for [memberRef] on any game or day. One rule for every query that wants "the
 * member's newest row", so the day stand-in and the account summary can never pick different rows.
 */
private const val NEWEST_ROW_OF_MEMBER =
    "SELECT rowid FROM ghosts WHERE memberRef = :memberRef ORDER BY fetchedAtMs DESC, day DESC, game LIMIT 1"

/**
 * The connections boards kept on this device, one board per game day. A board is only ever
 * swapped whole, so a reader never sees half of an old board mixed with half of a new one.
 */
@Dao
interface GhostDao {

    /** The board of one game day, in no guaranteed order. */
    @Query("SELECT * FROM ghosts WHERE game = :game AND day = :day")
    fun observe(game: String, day: String): Flow<List<GhostEntity>>

    /**
     * The board of one game day plus, when the day's board has no row of [memberRef], the last row read for
     * that member on any game or day. A member's name and photo belong to the member, not to a day, so the
     * player's own row of another day stands in for a day that never named them. One query, so a reader never
     * sees the stand-in alone while the day's rows are mid-write.
     */
    @Query(
        "SELECT * FROM ghosts WHERE (game = :game AND day = :day) OR (" +
            "NOT EXISTS (SELECT 1 FROM ghosts WHERE game = :game AND day = :day AND memberRef = :memberRef) " +
            "AND rowid = ($NEWEST_ROW_OF_MEMBER))",
    )
    fun observeWithNewest(game: String, day: String, memberRef: String): Flow<List<GhostEntity>>

    /** The last row read for [memberRef] on any game or day, the same row [observeWithNewest] stands in; null when none. */
    @Query("SELECT * FROM ghosts WHERE rowid = ($NEWEST_ROW_OF_MEMBER)")
    fun observeNewestOf(memberRef: String): Flow<GhostEntity?>

    /** How many distinct members other than [excluding] any stored board names. */
    @Query("SELECT COUNT(DISTINCT memberRef) FROM ghosts WHERE memberRef != :excluding")
    fun observeConnectionCount(excluding: String): Flow<Int>

    /** Swaps the board of one game day for [rows], atomically; an empty list clears it. */
    @Transaction
    suspend fun replace(game: String, day: String, rows: List<GhostEntity>) {
        deleteBoard(game, day)
        insertAll(rows)
    }

    @Query("DELETE FROM ghosts WHERE game = :game AND day = :day")
    suspend fun deleteBoard(game: String, day: String)

    /** Plain insert: [replace] has just deleted the board, so no row can conflict. */
    @Insert
    suspend fun insertAll(rows: List<GhostEntity>)

    /** Drops every board older than [day] (an ISO date); the boards of [day] itself stay. */
    @Query("DELETE FROM ghosts WHERE day < :day")
    suspend fun pruneBefore(day: String)

    @Query("DELETE FROM ghosts")
    suspend fun clear()
}
