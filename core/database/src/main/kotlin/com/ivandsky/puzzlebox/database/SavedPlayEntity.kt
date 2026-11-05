package com.ivandsky.puzzlebox.database

import androidx.room.Entity

/**
 * One saved in-progress play (schema v6, A10): the opaque session [blob] for one daily of
 * one game. [day] is the ISO-8601 date string; the primary key (gameId, day) means a newer
 * save of the same daily replaces the old one. [elapsedMs] is the play's displayed clock at
 * that save (the "In progress · m:ss" status). [updatedAt] (epoch ms) orders the slots and
 * decides which are pruned past the cap (SC8).
 */
@Entity(
    tableName = "saved_plays",
    primaryKeys = ["gameId", "day"],
)
class SavedPlayEntity(
    val gameId: String,
    val day: String,
    val blob: ByteArray,
    val elapsedMs: Long,
    val updatedAt: Long,
)
