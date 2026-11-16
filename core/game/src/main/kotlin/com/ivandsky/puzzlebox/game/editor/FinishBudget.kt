package com.ivandsky.puzzlebox.game.editor

/**
 * How long Finish for me may search for a target (90): [SHORT_MS] with no target and for Easy and
 * Medium, [LONG_MS] for Hard and Expert, which take more tries to hit. The Studio builds its
 * [Budget] from this, and a game's hit-rate measurements use the same numbers.
 */
object FinishBudget {
    const val SHORT_MS = 2_000L
    const val LONG_MS = 4_000L

    fun millisFor(target: Difficulty.Level?): Long = when (target) {
        Difficulty.Level.Hard, Difficulty.Level.Expert -> LONG_MS
        Difficulty.Level.Easy, Difficulty.Level.Medium, null -> SHORT_MS
    }
}
