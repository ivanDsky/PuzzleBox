package com.ivandsky.puzzlebox.game

import java.time.Instant

/**
 * Per-puzzle identity as the game authors it (finding F2-3 / ADR-0004). Record semantics
 * are per-puzzle, so a record without identity cannot exist. core/data keys and stamps
 * records per game + ref.
 */
interface PuzzleRef

/**
 * The open per-game stats slot (ADR-0005 §3): schema-versioned and opaque to core, so new
 * metrics land without schema churn. Never indexed; promoting one is an explicit one-time
 * core/data migration. Zip emits {"v":1,"variant":...,"mistakes":...,"backtracks":...}.
 */
class RecordStats(
    val schemaVersion: Int,
    val blob: String,
) {
    override fun equals(other: Any?): Boolean =
        other is RecordStats && schemaVersion == other.schemaVersion && blob == other.blob

    override fun hashCode(): Int = 31 * schemaVersion + blob.hashCode()

    override fun toString(): String = "RecordStats(v$schemaVersion, $blob)"
}

/**
 * Normalized completion emitted by a game (ADR-0004). Indexed semantics: [time] + [hints],
 * lower is better (A3) — mistakes are never recorded there, only in [stats] as analytics.
 * Games emit EVERY completion, replays included; [firstCompletedAt]/[isRecord] are stamped
 * by core/data (A4/A5) and are null/false as emitted.
 */
data class CompletionRecord(
    val puzzleRef: PuzzleRef,
    val time: Long,
    val hints: Int,
    val stats: RecordStats,
    val firstCompletedAt: Instant? = null,
    val isRecord: Boolean = false,
)
