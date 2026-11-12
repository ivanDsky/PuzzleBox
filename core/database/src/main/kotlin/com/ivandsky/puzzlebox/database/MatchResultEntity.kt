package com.ivandsky.puzzlebox.database

import androidx.room.Entity

/**
 * The outbox row of one result post of a duel leg (schema v8), keyed by ([matchCode],
 * [legIndex], [uid]). [timeMs], [hints] and [gaveUp] are the leg finish; [completedAtMs] is epoch
 * ms. [postState] is "Pending" | "Posted" | "Late" | "LocalOnly", retried [postAttempts] times.
 * [replay] (schema v12) is the leg's encoded solve replay, null when the leg has none.
 */
@Entity(tableName = "match_results", primaryKeys = ["matchCode", "legIndex", "uid"])
data class MatchResultEntity(
    val matchCode: String,
    val legIndex: Int,
    val uid: String,
    val timeMs: Long,
    val hints: Int,
    val completedAtMs: Long,
    val gaveUp: Boolean,
    val postState: String,
    val postAttempts: Int,
    val replay: ByteArray? = null,
)
