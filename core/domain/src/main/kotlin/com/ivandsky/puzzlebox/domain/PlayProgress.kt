package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId

/**
 * Today's progress across the playable games (A12, A13): [order] is the registry's game
 * order, [inProgress] the games with a save for their own today, [solved] the games with a
 * completion record for their own today. Solved wins: [inProgress] never holds a solved id.
 */
data class PlayProgress(val order: List<GameId>, val inProgress: Set<GameId>, val solved: Set<GameId>) {

    /** True when any of today's games has an unfinished save (the Play-tab dot). */
    val anyInProgress: Boolean get() = inProgress.isNotEmpty()

    /** The first unsolved game after [after] in [order], wrapping; never [after] itself.
     *  Null when every other game is solved. An [after] outside [order] starts at the top. */
    fun nextUnsolved(after: GameId): GameId? {
        val start = order.indexOf(after) + 1
        return (order.indices)
            .map { order[(start + it) % order.size] }
            .firstOrNull { it != after && it !in solved }
    }
}
