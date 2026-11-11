package com.ivandsky.puzzlebox.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One puzzle this user published (schema v7, A6), keyed by its share [code]. [authorTimeMs]
 * is the author's test-solve time; [solvesCount] is the last fetched solve count, taken at
 * [solvesCountAt] (epoch ms), both null until first fetched. [unpublished] marks a withdrawn code.
 */
@Entity(tableName = "published_puzzles")
class PublishedPuzzleEntity(
    @PrimaryKey val code: String,
    val gameId: String,
    val variant: String,
    val title: String,
    val authorTimeMs: Long,
    val createdAt: Long,
    val unpublished: Boolean,
    val solvesCount: Int?,
    val solvesCountAt: Long?,
    /** Hints the author used in the test solve behind [authorTimeMs] (M3). */
    val authorHints: Int,
    /** Schema v11 (cloud sync): the signed-in account that owns this row in the cloud; null until claimed. */
    val ownerUid: String? = null,
    /** Schema v11: fingerprint of the last version uploaded to the cloud; null when never uploaded. */
    val uploadedFingerprint: String? = null,
)
