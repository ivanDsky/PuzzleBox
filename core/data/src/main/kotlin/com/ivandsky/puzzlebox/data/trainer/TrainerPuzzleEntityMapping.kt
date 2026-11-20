package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.database.TrainerPuzzleEntity
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.Puzzle

/** Cache read: decode through the owning game's definition, the way the daily cache reads a row. Null when it no longer decodes. */
internal fun TrainerPuzzleEntity.toPuzzle(definition: GameDefinition): Puzzle? = definition.decode(payload).getOrNull()
