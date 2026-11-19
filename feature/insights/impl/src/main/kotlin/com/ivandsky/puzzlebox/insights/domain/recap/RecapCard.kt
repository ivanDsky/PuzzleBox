package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId

/**
 * One card of a recap: a closed set, so the feature's one card-to-page `when` stays exhaustive. A new card is a new
 * provider, a new variant here and its page.
 */
internal sealed interface RecapCard {

    /** Puzzles solved: the period's [total] and the [perGame] split, in the app's game order. */
    data class Solved(val total: Int, val perGame: List<PerGame>) : RecapCard {
        data class PerGame(val game: GameId, val solved: Int)
    }
}
