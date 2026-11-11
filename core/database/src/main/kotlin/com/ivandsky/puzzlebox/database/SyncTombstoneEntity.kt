package com.ivandsky.puzzlebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One pending cloud delete of a draft or received puzzle (schema v11, ADR-0029 §7), keyed by
 * ([kind], [docId], [ownerUid]): [kind] names the collection, [docId] the cloud document.
 * [deletedAtMs] is when the user deleted it locally; [eligibleAtMs] is the earliest epoch ms the
 * cloud delete may be sent. [uploaded] marks a delete already sent for [ownerUid].
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["kind", "docId", "ownerUid"])
data class SyncTombstoneEntity(
    val kind: String,
    val docId: String,
    val ownerUid: String,
    val deletedAtMs: Long,
    val eligibleAtMs: Long,
    @ColumnInfo(defaultValue = "0") val uploaded: Boolean = false,
)
