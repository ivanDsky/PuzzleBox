package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.game.CompletionRecord

/**
 * Pure A4/A5 decision core (JVM-tested, A17): the FIRST in-app completion of a puzzleRef
 * is the player's record; every later play of the same ref is a replay — stored, but it
 * NEVER mutates the record (A5: only records are leaderboard-eligible). Android-free so
 * the first-win rule stays provable without Room or Robolectric.
 */
internal class FirstWinPolicy {

    /** What to persist for one emitted completion. */
    sealed interface Decision {
        /** Insert as the ref's record row — the only isRecord row the ref will ever get. */
        data class Record(val completedAtMs: Long) : Decision

        /** Insert as a replay row; the existing record stays untouched (A4). */
        data class Replay(val completedAtMs: Long) : Decision
    }

    /**
     * @param hasRecord whether a record row already exists for the ref.
     * @param nowMs wall clock of this completion (injected by the caller; never re-read).
     */
    fun decide(hasRecord: Boolean, nowMs: Long): Decision =
        if (hasRecord) Decision.Replay(nowMs) else Decision.Record(nowMs)

    /**
     * The row to insert for an emitted record under [decision]: A3 score inputs pass
     * through untouched; the A4 stamps come from the decision, never from the emitted
     * flags (emitted firstCompletedAt/isRecord are null/false per the core/game contract).
     */
    fun stamp(record: CompletionRecord, refKey: String, decision: Decision): CompletionEntity {
        val (completedAtMs, isRecord) = when (decision) {
            is Decision.Record -> decision.completedAtMs to true
            is Decision.Replay -> decision.completedAtMs to false
        }
        return CompletionEntity(
            puzzleRef = refKey,
            timeMs = record.time,
            hints = record.hints,
            statsVersion = record.stats.schemaVersion,
            statsBlob = record.stats.blob,
            completedAtMs = completedAtMs,
            firstCompletedAtMs = if (isRecord) completedAtMs else null,
            isRecord = isRecord,
        )
    }
}
