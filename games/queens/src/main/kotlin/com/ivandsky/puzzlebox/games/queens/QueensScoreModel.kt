package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Emits the normalized completion (ADR-0004, queens-10 wiring), zip ZipScoreModel
 * conventions: ref-less boards are not record-eligible (ADR-0004: no identity, no
 * record). QueensSession v1 carries no timer or hint counters (the session tickets
 * never landed one), so the indexed time/hints are 0 — the leaderboard sanity envelope
 * (`time <= 0` is never eligible, LeaderboardEntries.fromRecord) keeps such rows
 * local-only history until a session timer lands. The ADR-0005 stats slot v1 carries
 * the variant, analytics only.
 */
class QueensScoreModel : ScoreModel<QueensSession> {

    override fun completion(session: QueensSession): CompletionRecord? {
        if (session.completedAt == null) return null
        val ref = session.ref ?: return null
        return CompletionRecord(
            puzzleRef = ref,
            time = 0L,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = statsBlob(session)),
        )
    }

    private fun statsBlob(session: QueensSession): String = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("variant", JsonPrimitive(queensVariantId(session.puzzle.size).value))
    }.toString()
}
