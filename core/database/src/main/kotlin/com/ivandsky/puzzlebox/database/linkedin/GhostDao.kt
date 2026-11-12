package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Whether a ghost row `g` belongs to a board the reader of owner `:owner` may see (ADR-0029 decision 6): no filter
 * when the owner is null, else the row's board (the saved board of its game and day) is ownerless or the owner's.
 * A ghost row has no owner of its own, it takes its board's.
 */
private const val VISIBLE_BOARD =
    "(:owner IS NULL OR EXISTS (SELECT 1 FROM saved_boards b WHERE b.game = g.game AND b.day = g.day " +
        "AND (b.ownerUid IS NULL OR b.ownerUid = :owner)))"

/**
 * The rowid of the last visible row read for [memberRef] on any game or day. One rule for every query that wants
 * "the member's newest row", so the day stand-in and the account summary can never pick different rows.
 */
private const val NEWEST_ROW_OF_MEMBER =
    "SELECT g.rowid FROM ghosts g WHERE g.memberRef = :memberRef AND $VISIBLE_BOARD " +
        "ORDER BY g.fetchedAtMs DESC, g.day DESC, g.game LIMIT 1"

/**
 * The connections boards kept on this device, one board per game day. A board is only ever
 * swapped whole, so a reader never sees half of an old board mixed with half of a new one.
 */
@Dao
interface GhostDao {

    /** The board of one game day, in no guaranteed order. */
    @Query("SELECT * FROM ghosts WHERE game = :game AND day = :day")
    fun observe(game: String, day: String): Flow<List<GhostEntity>>

    /** [observe] read once, for a reader that re-reads on its own trigger. */
    @Query("SELECT * FROM ghosts WHERE game = :game AND day = :day")
    suspend fun board(game: String, day: String): List<GhostEntity>

    /**
     * The last row read for [memberRef] on any game or day, from the boards [owner] may see (null: all of them);
     * null when none. It stands in for the player's own row on a day whose board names them not.
     */
    @Query("SELECT * FROM ghosts WHERE rowid = ($NEWEST_ROW_OF_MEMBER)")
    fun observeNewestOf(memberRef: String, owner: String?): Flow<GhostEntity?>

    /** [observeNewestOf] read once. */
    @Query("SELECT * FROM ghosts WHERE rowid = ($NEWEST_ROW_OF_MEMBER)")
    suspend fun newestOf(memberRef: String, owner: String?): GhostEntity?

    /** How many distinct members other than [excluding] the boards [owner] may see (null: all) name. */
    @Query("SELECT COUNT(DISTINCT g.memberRef) FROM ghosts g WHERE g.memberRef != :excluding AND $VISIBLE_BOARD")
    fun observeConnectionCount(excluding: String, owner: String?): Flow<Int>

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

    @Query("DELETE FROM ghosts")
    suspend fun clear()
}
