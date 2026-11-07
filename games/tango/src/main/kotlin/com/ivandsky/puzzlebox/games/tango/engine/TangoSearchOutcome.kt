package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell

/** A budgeted [TangoUniqueness.bounded] run: settled with up to `limit` solutions, or stopped by the budget. */
sealed interface TangoSearchOutcome {
    data class Done(val solutions: List<List<TangoCell>>) : TangoSearchOutcome
    data object Exhausted : TangoSearchOutcome
}
