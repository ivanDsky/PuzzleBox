package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot

/**
 * Firestore document ↔ entry codec. The doc payload IS the A19 versioned envelope:
 * every entry self-describes `formatVersion`; readers preserve-or-drop (unknown map
 * keys are ignored, unknown versions are dropped, known fields are never reinterpreted).
 */
internal object LeaderboardDocCodec {

    const val FORMAT_VERSION = LeaderboardSnapshot.FORMAT_VERSION

    const val FIELD_UID = "uid"
    const val FIELD_DISPLAY_NAME = "displayName"
    const val FIELD_PUZZLE_REF = "puzzleRef"
    const val FIELD_TIME = "timeMs"
    const val FIELD_HINTS = "hints"
    const val FIELD_COMPLETED_AT = "completedAtMs"
    const val FIELD_FORMAT_VERSION = "formatVersion"

    fun encode(entry: LeaderboardEntry): Map<String, Any?> = mapOf(
        FIELD_UID to entry.uid,
        FIELD_DISPLAY_NAME to entry.displayName,
        FIELD_PUZZLE_REF to entry.puzzleRef,
        FIELD_TIME to entry.timeMs,
        FIELD_HINTS to entry.hints,
        FIELD_COMPLETED_AT to entry.completedAtMs,
        FIELD_FORMAT_VERSION to FORMAT_VERSION,
    )

    /**
     * Null when the doc may not appear on a board: unknown version, malformed fields, or a
     * puzzleRef [acceptsRef] refuses (by default: not an official daily).
     */
    fun decode(
        data: Map<String, Any?>,
        acceptsRef: (String) -> Boolean = { LeaderboardRef.fromPuzzleRefKey(it) != null },
    ): LeaderboardEntry? {
        val version = (data[FIELD_FORMAT_VERSION] as? Number)?.toInt() ?: return null
        // A19: a future format may reinterpret known fields — a v1 reader drops it.
        if (version != FORMAT_VERSION) return null
        val uid = data[FIELD_UID] as? String ?: return null
        val displayName = data[FIELD_DISPLAY_NAME] as? String ?: return null
        val puzzleRef = data[FIELD_PUZZLE_REF] as? String ?: return null
        val timeMs = (data[FIELD_TIME] as? Number)?.toLong() ?: return null
        val hints = (data[FIELD_HINTS] as? Number)?.toInt() ?: return null
        val completedAtMs = (data[FIELD_COMPLETED_AT] as? Number)?.toLong() ?: return null
        if (uid.isEmpty()) return null
        if (timeMs <= 0 || hints < 0 || completedAtMs <= 0) return null
        // Same ref shape the Firestore rules enforce server-side.
        if (!acceptsRef(puzzleRef)) return null
        return LeaderboardEntry(
            uid = uid,
            displayName = displayName,
            puzzleRef = puzzleRef,
            timeMs = timeMs,
            hints = hints,
            completedAtMs = completedAtMs,
        )
    }
}
