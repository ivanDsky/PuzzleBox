package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Entity

/**
 * One LinkedIn connection's result for one game day, as last read from the connections
 * board. It lives in its own database file so backup and device transfer can leave the
 * whole file out, never reaches the leaderboard types, and is synced to the owner's cloud space when signed in.
 * [memberRef] is the connection's profile id and [day] an ISO date, so days compare as text.
 */
@Entity(tableName = "ghosts", primaryKeys = ["game", "day", "memberRef"])
data class GhostEntity(
    val game: String,
    val day: String,
    val memberRef: String,
    val name: String,
    /** Null when the connection hides the time or has not opted in. */
    val timeMs: Long?,
    val hintFree: Boolean,
    val flawless: Boolean,
    /** When this board was read (epoch ms). */
    val fetchedAtMs: Long,
)
