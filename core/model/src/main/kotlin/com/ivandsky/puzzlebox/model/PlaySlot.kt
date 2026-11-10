package com.ivandsky.puzzlebox.model

import java.time.LocalDate

/**
 * The identity of one play, with at most one saved play each: a [Daily] of one game,
 * a friend's puzzle ([Custom]), or one puzzle of a duel ([Leg]); friend puzzles and
 * duel puzzles are each saved in their own store with their own cap.
 */
sealed interface PlaySlot {
    val gameId: GameId

    /** One daily ([day]) of one game ([gameId]). */
    data class Daily(override val gameId: GameId, val day: LocalDate) : PlaySlot

    /** One friend's puzzle ([puzzleId], the puzzle's identity) played in [gameId]. */
    data class Custom(override val gameId: GameId, val puzzleId: String) : PlaySlot

    /** One leg of a duel; its own store and cap, never resumes or evicts a daily or friend save. */
    data class Leg(override val gameId: GameId, val matchCode: String, val legIndex: Int) : PlaySlot
}
