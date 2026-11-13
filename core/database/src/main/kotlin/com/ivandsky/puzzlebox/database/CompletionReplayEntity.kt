package com.ivandsky.puzzlebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The solve replay of one completion (schema v12), in its own table beside `completions` so the
 * per-game stats slot stays as it is. [replay] is the encoded move log, opaque to the database;
 * the row is deleted with its completion. [uploaded] (schema v13, cloud sync) marks a replay the cloud holds for the
 * completion's owner.
 */
@Entity(
    tableName = "completion_replays",
    foreignKeys = [
        ForeignKey(
            entity = CompletionEntity::class,
            parentColumns = ["id"],
            childColumns = ["completionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class CompletionReplayEntity(
    @PrimaryKey val completionId: Long,
    val replay: ByteArray,
    @ColumnInfo(defaultValue = "0") val uploaded: Boolean = false,
)
