package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId

/**
 * The first game after [after] in this set's order that is not [TodayStatus.Solved],
 * wrapping; never [after] itself. Null when every other game is solved. An [after] outside
 * the set starts at the top (Result's "Next: <game>", A13).
 */
fun List<TodayEntry>.nextUnsolved(after: GameId): GameId? {
    val start = indexOfFirst { it.game == after } + 1
    return indices
        .map { this[(start + it) % size] }
        .firstOrNull { it.game != after && it.status !is TodayStatus.Solved }
        ?.game
}
