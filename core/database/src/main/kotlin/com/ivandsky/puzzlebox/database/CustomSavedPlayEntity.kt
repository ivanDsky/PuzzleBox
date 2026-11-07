package com.ivandsky.puzzlebox.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved in-progress play of a friend's puzzle (schema v7, A13): the opaque session
 * [blob] keyed by [puzzleId], so a newer save replaces the old one. [elapsedMs] is the
 * displayed clock at that save; [updatedAt] (epoch ms) orders the slots.
 */
@Entity(tableName = "custom_saved_plays")
class CustomSavedPlayEntity(
    @PrimaryKey val puzzleId: String,
    val gameId: String,
    val blob: ByteArray,
    val elapsedMs: Long,
    val updatedAt: Long,
)
