package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Emits the completion record (ADR-0004) once every word is found. Fewer hints rank first,
 * time breaks ties (A25). A friend's custom puzzle always scores; a daily without a puzzle
 * number is not record-eligible. The stats slot v1 carries the non-theme word count, analytics only.
 */
object StrandsScoreModel : ScoreModel<StrandsSession> {

    override val rankOrder: RankOrder = RankOrder.HintsThenTime

    override fun completion(session: StrandsSession): CompletionRecord? {
        if (!session.state.isComplete) return null
        val ref = session.ref?.takeIf(::isRecordEligible) ?: return null
        return CompletionRecord(
            puzzleRef = ref,
            time = session.elapsedMs,
            hints = session.state.hintsUsed,
            stats = RecordStats(schemaVersion = 1, blob = statsBlob(session.state)),
        )
    }

    /** A friend's custom puzzle always scores; a daily only with its number (spec A11, A12). */
    private fun isRecordEligible(ref: StrandsPuzzleRef): Boolean = when (val key = ref.key) {
        is PuzzleKey.Custom -> true
        is PuzzleKey.Daily -> key.puzzleNumber != null
    }

    private fun statsBlob(state: StrandsState): String = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("nonTheme", JsonPrimitive(state.nonTheme.size))
    }.toString()
}
