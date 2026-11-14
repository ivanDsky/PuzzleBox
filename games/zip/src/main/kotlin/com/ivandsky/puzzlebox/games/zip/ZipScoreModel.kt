package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Emits the normalized completion (ADR-0004): indexed semantics stay A3 —
 * time = [ZipSession.elapsedMs] (active play only), hints = [ZipSession.hintsUsed] —
 * and the ADR-0005 stats slot v1 carries the variant plus the §10 mistake/backtrack
 * counters, analytics only, never indexed, never scored. Every completion emits,
 * play-agains included; core/data stamps firstCompletedAt/isRecord (A4/A5).
 *
 * Custom boards (no [ZipPuzzleRef]) are not record-eligible until Phase 2 gives custom
 * puzzles an identity — completion() returns null for them.
 */
class ZipScoreModel : ScoreModel<ZipSession> {

    override fun completion(session: ZipSession): CompletionRecord? {
        if (session.completedAt == null) return null
        val ref = session.ref ?: return null
        return CompletionRecord(
            puzzleRef = ref,
            time = session.elapsedMs,
            hints = session.hintsUsed,
            stats = RecordStats(schemaVersion = 1, blob = statsBlob(session)),
        )
    }

    private fun statsBlob(session: ZipSession): String = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("variant", JsonPrimitive(session.puzzle.variant.value))
        put("mistakes", JsonPrimitive(session.mistakeCount))
        put("backtracks", JsonPrimitive(session.backtrackCount))
    }.toString()
}
