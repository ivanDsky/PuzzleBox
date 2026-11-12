package com.ivandsky.puzzlebox.game.replay

/** What a compare reads from one board. */
data class ReplayFacts(val hints: Int, val correct: Int, val total: Int, val solved: Boolean)
