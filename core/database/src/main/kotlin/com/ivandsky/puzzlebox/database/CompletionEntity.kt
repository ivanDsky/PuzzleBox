package com.ivandsky.puzzlebox.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The completions store (schema v3, ticket 15): every in-app completion of a puzzleRef is
 * kept — exactly one row per ref carries isRecord=1 (A4 first-win-is-record, enforced by
 * FirstWinPolicy), later plays are replay rows kept for stats and never mutate the record
 * (A5: only records are leaderboard-eligible). [statsBlob]/[statsVersion] are the A19
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
    /** A3 score inputs, stored exactly as emitted; lower is better, mistakes stay in stats. */
    val timeMs: Long,
    val hints: Int,
    val statsVersion: Int,
    val statsBlob: String,
    /** When THIS completion happened; on the record row this is the first-win instant. */
    val completedAtMs: Long,
    /** A4 stamp — set only on the record row; replay rows keep null, as emitted. */
    val firstCompletedAtMs: Long?,
    val isRecord: Boolean,
    /** Schema v8: the duel's match code when this puzzle was played as a duel leg, else null. */
    val matchCode: String? = null,
)
