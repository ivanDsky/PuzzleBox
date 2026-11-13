package com.ivandsky.puzzlebox.database.finalboard

import androidx.room.Entity
import androidx.room.ForeignKey

/** One player's entry on a stored Final board; deleted with its header. */
@Entity(
    tableName = "final_board_entries",
    primaryKeys = ["gameId", "day", "uid"],
    foreignKeys = [
        ForeignKey(
            entity = FinalBoardEntity::class,
            parentColumns = ["gameId", "day"],
            childColumns = ["gameId", "day"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FinalBoardEntryEntity(
    val gameId: String,
    val day: String,
    val uid: String,
    val displayName: String,
    val puzzleRef: String,
    val timeMs: Long,
    val hints: Int,
    val completedAtMs: Long,
)
