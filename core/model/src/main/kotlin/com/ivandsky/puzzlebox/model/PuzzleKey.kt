package com.ivandsky.puzzlebox.model

import java.time.LocalDate

/**
 * The identity of one puzzle within a game (ADR-0018, spec A11): either a day's daily or a
 * friend's shared puzzle. Together with a [GameId] it names exactly one playable puzzle.
 */
sealed interface PuzzleKey {
    data class Daily(val date: LocalDate, val puzzleNumber: Int?) : PuzzleKey

    /** [puzzleId] is the canonical share code (6 Crockford chars, upper case, no dash). */
    data class Custom(val puzzleId: String) : PuzzleKey {
        companion object {
            /** Reserved GameId and the leading segment of a custom ref key. */
            const val NAMESPACE: String = "custom"
        }
    }
}
