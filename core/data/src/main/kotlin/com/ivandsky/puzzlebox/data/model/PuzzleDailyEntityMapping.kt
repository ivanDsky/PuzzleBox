package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.database.PuzzleDailyEntity
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey

/** Cache read: decode through the owning game's definition — one decode path for every reader. */
internal fun PuzzleDailyEntity.toPuzzle(definition: GameDefinition): Puzzle? = definition.decode(payload).getOrNull()

/**
 * Cache write: identity + envelope in one row (the wire carries no date; ref comes from the
 * fetch day). Only a DailySource fetch ever reaches here, so [ref] is always a daily.
 */
internal fun Puzzle.toEntity(
    ref: PuzzleRef,
    fetchedAtMs: Long,
): PuzzleDailyEntity {
    val key = ref.key
    require(key is PuzzleKey.Daily) { "the daily cache stores daily refs only" }
    return PuzzleDailyEntity(
        gameId = gameId.value,
        day = key.date.toString(),
        puzzleNumber = key.puzzleNumber,
        variant = variant.value,
        formatVersion = formatVersion,
        payload = payload,
        fetchedAtMs = fetchedAtMs,
    )
}
