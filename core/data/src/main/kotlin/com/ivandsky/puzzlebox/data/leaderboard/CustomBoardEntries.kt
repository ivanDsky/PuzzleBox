package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.PuzzleKey

/**
 * Derives a friend puzzle's board entry from a completion record. Daily records never map here,
 * and [LeaderboardEntries] never maps a friend puzzle, so the two boards stay apart.
 */
object CustomBoardEntries {

    /**
     * Null unless [record] is the first win of a custom puzzle with a score the board's rules
     * accept (timeMs > 0, hints >= 0, a positive first-win stamp).
     */
    fun fromRecord(record: CompletionRecord, uid: String, displayName: String): LeaderboardEntry? {
        if (!record.isRecord) return null // play-agains never reach a board
        if (record.puzzleRef.key !is PuzzleKey.Custom) return null
        if (record.time <= 0 || record.hints < 0) return null
        val completedAtMs = record.firstCompletedAt?.toEpochMilli()?.takeIf { it > 0 } ?: return null
        return LeaderboardEntry(
            uid = uid,
            displayName = LeaderboardEntries.normalizeDisplayName(displayName),
            puzzleRef = PuzzleRefKeys.encode(record.puzzleRef),
            timeMs = record.time,
            hints = record.hints,
            completedAtMs = completedAtMs,
        )
    }
}
