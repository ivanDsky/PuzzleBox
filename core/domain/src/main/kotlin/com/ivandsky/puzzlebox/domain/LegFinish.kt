package com.ivandsky.puzzlebox.domain

/** How a duel leg ended: solved with the game's own time and hints, or given up (a loss, never a draw). */
sealed interface LegFinish {

    data class Solved(val timeMs: Long, val hints: Int) : LegFinish

    data object GaveUp : LegFinish
}
