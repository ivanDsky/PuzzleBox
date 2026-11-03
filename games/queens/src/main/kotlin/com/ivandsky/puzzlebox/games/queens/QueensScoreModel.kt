package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Emits the normalized completion (ADR-0004, queens-10 wiring), zip ZipScoreModel
 * conventions: ref-less boards are not record-eligible (ADR-0004: no identity, no
 * record). Fix round 6 landed the session timer + hint counter, so the indexed facts
 * are the REAL play duration ([QueensSession.elapsedMs], first action → win stamp) and
 * presented-hint count — the leaderboard sanity envelope (`time <= 0` is never
 * eligible) now admits genuinely played records. The ADR-0005 stats slot v1 carries
 * the variant, analytics only.
 */
class QueensScoreModel : ScoreModel<QueensSession> {

    override fun completion(session: QueensSession): CompletionRecord? {
        if (session.completedAt == null) return null
        val ref = session.ref ?: return null
        return CompletionRecord(
            puzzleRef = ref,
            time = session.elapsedMs() ?: 0L,
            hints = session.hintsUsed,
            stats = RecordStats(schemaVersion = 1, blob = statsBlob(session)),
        )
    }

    private fun statsBlob(session: QueensSession): String = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("variant", JsonPrimitive(queensVariantId(session.puzzle.size).value))
    }.toString()
}
