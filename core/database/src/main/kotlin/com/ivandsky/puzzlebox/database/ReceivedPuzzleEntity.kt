package com.ivandsky.puzzlebox.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One puzzle received from a friend (schema v7, A6), keyed by its share [code].
 * [puzzleBytes] is the verified canonical Puzzle incl. the re-derived solution, in shared
 * format [sharedFormatVersion] (ADR-0005). [postState] is "Pending" | "Posted" | "LocalOnly"
 * for this user's result post, retried [postAttempts] times. [openedAt] is epoch ms.
 */
@Entity(tableName = "received_puzzles")
class ReceivedPuzzleEntity(
    @PrimaryKey val code: String,
    val gameId: String,
    val variant: String,
    val sharedFormatVersion: Int,
    val puzzleBytes: ByteArray,
    val title: String,
    val authorName: String,
    val note: String,
    val authorTimeMs: Long,
    val authorHints: Int,
    val authorUid: String,
    val openedAt: Long,
    val unpublished: Boolean,
    val postState: String,
    val postAttempts: Int,
    /** The author's difficulty label, null when the share carried none (M3). */
    val difficulty: String?,
)
