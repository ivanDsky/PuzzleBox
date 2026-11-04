package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.database.PuzzleDailyEntity
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef

/** Cache read: decode through the owning game's definition — one decode path for every reader. */
internal fun PuzzleDailyEntity.toPuzzle(definition: GameDefinition): Puzzle? = definition.decode(payload).getOrNull()

/** Cache write: identity + envelope in one row (the wire carries no date; ref comes from the fetch day). */
internal fun Puzzle.toEntity(
    ref: PuzzleRef,
    fetchedAtMs: Long,
): PuzzleDailyEntity = PuzzleDailyEntity(
    gameId = gameId.value,
    day = ref.date.toString(),
    puzzleNumber = ref.puzzleNumber,
    variant = variant.value,
    formatVersion = formatVersion,
    payload = payload,
    fetchedAtMs = fetchedAtMs,
)
