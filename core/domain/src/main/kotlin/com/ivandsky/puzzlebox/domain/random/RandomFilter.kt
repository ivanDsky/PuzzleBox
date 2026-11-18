package com.ivandsky.puzzlebox.domain.random

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId

/** Which pool a random pick draws from. A new pool is a new subtype, a use case and one branch in [LoadRandomPuzzle]. */
sealed interface RandomFilter {
    /** Past days of the daily archive: one [game], or every game with an archive when null. */
    data class Archive(val game: GameId?, val unsolvedOnly: Boolean) : RandomFilter

    /** New today: the community list, narrowed to [game] (null: any) and [difficulties] (empty: any). */
    data class Community(val game: GameId?, val difficulties: Set<Difficulty.Level>) : RandomFilter
}
