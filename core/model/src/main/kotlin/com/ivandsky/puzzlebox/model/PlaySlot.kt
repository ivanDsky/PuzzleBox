package com.ivandsky.puzzlebox.model

import java.time.LocalDate

/**
 * The identity of one play, with at most one saved play each (A10): a [Daily] of one game,
 * or a friend's puzzle ([Custom], A13), which is saved in its own store with its own cap.
 */
sealed interface PlaySlot {
    val gameId: GameId

    /** One daily ([day]) of one game ([gameId]). */
    data class Daily(override val gameId: GameId, val day: LocalDate) : PlaySlot

    /** One friend's puzzle ([puzzleId], the A11 identity) played in [gameId]. */
    data class Custom(override val gameId: GameId, val puzzleId: String) : PlaySlot
}
