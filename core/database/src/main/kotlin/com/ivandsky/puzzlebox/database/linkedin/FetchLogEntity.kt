package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Entity

/**
 * The last successful read of one item of one game day for one profile: what was asked,
 * when, and what came back. It drives the cache windows, so nothing is asked of LinkedIn
 * twice without need. Keyed by [profileId] so another account never inherits a cached answer.
 *
 * [item] and [outcome] are enum names persisted as plain text; the enums live in the data
 * layer, which keeps this module free of them.
 */
@Entity(tableName = "fetch_log", primaryKeys = ["game", "day", "item", "profileId"])
data class FetchLogEntity(
    val game: String,
    /** ISO date. */
    val day: String,
    /** "Own" or "Board". */
    val item: String,
    val profileId: String,
    /** When the read succeeded (epoch ms). */
    val fetchedAtMs: Long,
    /** "Solved", "NotPlayed", "Board" or "BoardToday". */
    val outcome: String,
)
