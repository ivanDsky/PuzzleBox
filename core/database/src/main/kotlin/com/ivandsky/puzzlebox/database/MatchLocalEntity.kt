package com.ivandsky.puzzlebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This device's own memory of a duel, keyed by the match [code]. [role] is
 * "Invite" | "Participant"; [openedAt] and [revealSeenAt] are epoch ms, the latter null until
 * the reveal was seen. [revealMyScore] and [revealTheirScore] are the puzzles each side won as the
 * reveal showed them, written together with [revealSeenAt] and null for a reveal seen before they were kept.
 * [dismissed] hides the duel from the list; it defaults to false.
 */
@Entity(tableName = "matches_local")
data class MatchLocalEntity(
    @PrimaryKey val code: String,
    val role: String,
    val opponentName: String?,
    val openedAt: Long,
    val revealSeenAt: Long?,
    @ColumnInfo(defaultValue = "0") val dismissed: Boolean = false,
    val revealMyScore: Int? = null,
    val revealTheirScore: Int? = null,
)
