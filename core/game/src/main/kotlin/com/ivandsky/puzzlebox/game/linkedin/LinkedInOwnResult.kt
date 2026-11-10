package com.ivandsky.puzzlebox.game.linkedin

sealed interface LinkedInOwnResult {
    data class Solved(val timeSeconds: Int, val hintFree: Boolean) : LinkedInOwnResult

    /** Didn't play, or a null/0 score. Nothing downstream records a completion for it. */
    data object NotPlayed : LinkedInOwnResult
}
