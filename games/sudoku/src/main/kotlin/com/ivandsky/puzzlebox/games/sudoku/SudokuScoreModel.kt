package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The completion record: banked play time and hints used. A board without a ref (an
 * imported or test board) has no daily identity, so it earns no record (ADR-0004).
 */
class SudokuScoreModel : ScoreModel<SudokuSession> {

    override fun completion(session: SudokuSession): CompletionRecord? {
        if (session.completedAt == null) return null
        val ref = session.ref ?: return null
        return CompletionRecord(
            puzzleRef = ref,
            time = session.elapsedMs,
            hints = session.hintsUsed,
            stats = RecordStats(schemaVersion = 1, blob = statsBlob(session)),
        )
    }

    private fun statsBlob(session: SudokuSession): String = buildJsonObject {
        put("v", JsonPrimitive(1))
        put("variant", JsonPrimitive(session.puzzle.variant.id.value))
    }.toString()
}
