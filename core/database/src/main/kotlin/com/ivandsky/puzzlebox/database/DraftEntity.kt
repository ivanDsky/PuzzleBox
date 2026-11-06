package com.ivandsky.puzzlebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One editor draft (schema v7, A6): the opaque draft [blob] in format [draftFormatVersion]
 * (ADR-0005) for one [variant] of one game. The test-solve fields stay null until the author
 * solves the draft on [testLayoutKey]; [reservedCode] is the share code once reserved;
 * [imported] marks a draft copied in from elsewhere. [updatedAt] is epoch ms.
 */
@Entity(tableName = "drafts")
class DraftEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val variant: String,
    val draftFormatVersion: Int,
    val blob: ByteArray,
    val title: String,
    val updatedAt: Long,
    val testLayoutKey: String?,
    val testTimeMs: Long?,
    val testHints: Int?,
    val reservedCode: String?,
    val imported: Boolean,
    /** M3 publish details; "" until the author sets them. */
    @ColumnInfo(defaultValue = "") val authorName: String,
    @ColumnInfo(defaultValue = "") val note: String,
)
