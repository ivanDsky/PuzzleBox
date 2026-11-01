package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.data.db.CompletionEntity
import com.ivandsky.puzzlebox.data.db.PuzzleRefKeys
import com.ivandsky.puzzlebox.game.CompletionRecord

/**
 * Pure A3/A5 derivation from the records store to a leaderboard entry (brief §1):
 * records only (A5 — replay rows are never eligible), official-daily shape only, and
 * the same sanity envelope the Firestore rules enforce (timeMs > 0, hints >= 0).
 * Android-free so the rule stays provable without Room or Robolectric.
 */
object LeaderboardEntries {

    /** From the Room row. Null when this row may never appear on a board. */
    fun fromEntity(entity: CompletionEntity, uid: String, displayName: String): LeaderboardEntry? {
        if (!entity.isRecord) return null // A5: replays are never leaderboard-eligible
        if (LeaderboardRef.fromPuzzleRefKey(entity.puzzleRef) == null) return null
        if (entity.timeMs <= 0 || entity.hints < 0) return null // rules sanity envelope
        return LeaderboardEntry(
            uid = uid,
            displayName = displayName,
            puzzleRef = entity.puzzleRef,
            timeMs = entity.timeMs,
            hints = entity.hints,
            completedAtMs = entity.firstCompletedAtMs ?: entity.completedAtMs,
        )
    }

    /**
     * From the model [com.ivandsky.puzzlebox.data.repository.CompletionRepository]
     * emits (the submit-watcher path). Null when this completion may never appear on
     * a board; a ref no registered game authored decodes to null (A19 preserve-or-drop).
     */
    fun fromRecord(record: CompletionRecord, uid: String, displayName: String): LeaderboardEntry? {
        if (!record.isRecord) return null // A5: replays are never leaderboard-eligible
        // A ref no registered game authored cannot key a completion (A19 preserve-or-drop).
        val refKey = runCatching { PuzzleRefKeys.encode(record.puzzleRef) }.getOrNull()
            ?: return null
        if (LeaderboardRef.fromPuzzleRefKey(refKey) == null) return null
        if (record.time <= 0 || record.hints < 0) return null // rules sanity envelope
        // A record row always carries the A4 first-win stamp; without it there is no
        // honest completedAt to publish and the rules would reject the write.
        val completedAtMs = record.firstCompletedAt?.toEpochMilli() ?: return null
        return LeaderboardEntry(
            uid = uid,
            displayName = displayName,
            puzzleRef = refKey,
            timeMs = record.time,
            hints = record.hints,
            completedAtMs = completedAtMs,
        )
    }
}
