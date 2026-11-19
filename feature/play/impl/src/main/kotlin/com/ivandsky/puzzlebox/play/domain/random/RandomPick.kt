package com.ivandsky.puzzlebox.play.domain.random

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate

/** One picked puzzle. */
sealed interface RandomPick {
    /** A past day of [game]'s archive; it opened when it was picked. */
    data class Day(val game: GameId, val day: LocalDate) : RandomPick

    /** A New today puzzle by its share [code]. */
    data class Shared(val code: ShareCode) : RandomPick
}
