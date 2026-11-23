package com.ivandsky.puzzlebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One technique's Trainer progress for one owner (schema v18): its [mark], the right answers in a row ([inRow]) and
 * when the last answer was recorded ([answeredAtMs]). It always syncs as the `trainer` kind.
 *
 * [ownerUid] follows the other synced rows (ADR-0029): null means nobody was signed in, and the first account to
 * sign in claims it. SQLite treats NULLs as distinct, so no unique index can hold one row per (owner, game,
 * technique); [TrainerProgressDao.upsert] keeps it instead. [mark] is persisted text and later wire data, so the
 * repository maps it explicitly and never by enum name. [uploaded] is false until the row reaches the cloud.
 */
@Entity(
    tableName = "trainer_progress",
    indices = [Index(value = ["gameId", "ownerUid"])],
)
data class TrainerProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String?,
    val gameId: String,
    val techniqueId: String,
    val mark: String,
    val inRow: Int,
    val answeredAtMs: Long,
    @ColumnInfo(defaultValue = "0") val uploaded: Boolean = false,
)
