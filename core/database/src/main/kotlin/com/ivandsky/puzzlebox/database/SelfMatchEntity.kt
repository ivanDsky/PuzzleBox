package com.ivandsky.puzzlebox.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One self duel (you against your own first solves), keyed by its locally generated match [code].
 * [roundCount], [games] and [includeFriends] are the settings the duel was created with, kept as columns so
 * Rematch can rebuild them; [games] and [legs] are the owning layer's encoded lists, opaque to the database.
 * [createdAtMs] is epoch ms.
 */
@Entity(tableName = "self_matches")
data class SelfMatchEntity(
    @PrimaryKey val code: String,
    val createdAtMs: Long,
    val roundCount: Int,
    val games: String,
    val includeFriends: Boolean,
    val legs: String,
)
