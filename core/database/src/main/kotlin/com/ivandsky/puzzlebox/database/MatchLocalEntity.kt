package com.ivandsky.puzzlebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This device's own memory of a duel (schema v8), keyed by the match [code]. [role] is
 * "Invite" | "Participant"; [openedAt] and [revealSeenAt] are epoch ms, the latter null until
 * the reveal was seen. [dismissed] hides the duel from the list; it defaults to false.
 */
@Entity(tableName = "matches_local")
data class MatchLocalEntity(
    @PrimaryKey val code: String,
    val role: String,
    val opponentName: String?,
    val openedAt: Long,
    val revealSeenAt: Long?,
    @ColumnInfo(defaultValue = "0") val dismissed: Boolean = false,
)
