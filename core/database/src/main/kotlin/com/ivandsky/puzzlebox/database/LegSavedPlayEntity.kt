package com.ivandsky.puzzlebox.database

import androidx.room.Entity

/**
 * One saved in-progress play of one puzzle of a duel (schema v8): the opaque session
 * [blob] keyed by ([matchCode], [legIndex]), in its own store so it never resumes or evicts a
 * daily or friend save. [elapsedMs] is the displayed clock at that save; [updatedAt] (epoch ms)
 * orders the slots.
 */
@Entity(tableName = "leg_saved_plays", primaryKeys = ["matchCode", "legIndex"])
class LegSavedPlayEntity(
    val matchCode: String,
    val legIndex: Int,
    val gameId: String,
    val blob: ByteArray,
    val elapsedMs: Long,
    val updatedAt: Long,
)
