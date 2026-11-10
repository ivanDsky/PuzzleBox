package com.ivandsky.puzzlebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The completions store (schema v3): every in-app completion of a puzzleRef is
 * kept — exactly one row per ref carries isRecord=1 (the first win is the record, enforced by
 * FirstWinPolicy), later plays are replay rows kept for stats and never mutate the record
 * (only records are leaderboard-eligible). [statsBlob]/[statsVersion] are the
 * self-describing per-game stats slot (ADR-0004): opaque to core, never indexed; promoting
 * a stat to a column is an explicit one-time migration. [puzzleRef] is the composite
 * identity key (core:data's PuzzleRefKeys).
 */
@Entity(
    tableName = "completions",
    indices = [Index("puzzleRef")],
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puzzleRef: String,
    /** Score inputs, stored exactly as emitted; lower is better, mistakes stay in stats. */
    val timeMs: Long,
    val hints: Int,
    val statsVersion: Int,
    val statsBlob: String,
    /** When THIS completion happened; on the record row this is the first-win instant. */
    val completedAtMs: Long,
    /** Record stamp — set only on the record row; replay rows keep null, as emitted. */
    val firstCompletedAtMs: Long?,
    val isRecord: Boolean,
    /** Schema v8: the duel's match code when this puzzle was played as a duel leg, else null. */
    val matchCode: String? = null,
    /** Schema v9: where the completion came from — 'app' when earned in the app, 'linkedin' for an import. */
    @ColumnInfo(defaultValue = "app") val source: String = "app",
)
