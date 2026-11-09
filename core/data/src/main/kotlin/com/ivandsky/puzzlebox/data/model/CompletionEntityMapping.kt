package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import java.time.Instant

/** Cache read: decode through the composite-identity key — one decode path for every reader. */
internal fun CompletionEntity.toModel(registry: GameRegistry): CompletionRecord? {
    val ref = PuzzleRefKeys.decode(puzzleRef, registry) ?: return null
    return CompletionRecord(
        puzzleRef = ref,
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = statsVersion, blob = statsBlob),
        firstCompletedAt = firstCompletedAtMs?.let(Instant::ofEpochMilli),
        isRecord = isRecord,
        matchCode = matchCode,
    )
}
