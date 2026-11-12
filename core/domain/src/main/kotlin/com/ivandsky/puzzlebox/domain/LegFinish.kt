package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.SolveReplay

/** How a duel leg ended: solved with the game's own time and hints, or given up (a loss, never a draw). */
sealed interface LegFinish {

    /** [replay] is advisory: it rides the queued result and never changes the time, hints or outcome. */
    data class Solved(val timeMs: Long, val hints: Int, val replay: SolveReplay? = null) : LegFinish

    data class GaveUp(val replay: SolveReplay? = null) : LegFinish
}
