package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Emits the normalized completion (ADR-0004) in Zip's shape (A4): time = [TangoSession.elapsedMs],
 * hints = [TangoSession.hintsUsed]; the ADR-0005 stats slot v1 carries variant + mistake
 * counter, analytics only. Custom boards (no [TangoPuzzleRef]) are not record-eligible —
 * completion() returns null, Zip precedent.
 */
class TangoScoreModel : ScoreModel<TangoSession> {

    override fun completion(session: TangoSession): CompletionRecord? {
        if (session.completedAt == null) return null
        val ref = session.ref ?: return null
        return CompletionRecord(
            puzzleRef = ref,
            time = session.elapsedMs,
            hints = session.hintsUsed,
            stats = RecordStats(schemaVersion = 1, blob = statsBlob(session)),
        )
    }

    private fun statsBlob(session: TangoSession): String = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("variant", JsonPrimitive(session.puzzle.variant.value))
        put("mistakes", JsonPrimitive(session.mistakeCount))
    }.toString()
}
