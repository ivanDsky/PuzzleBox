package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

/** Outcome of a full ladder run: solved or stalled, the hardest rung used, the step count. */
data class QueensLadderRun(val solved: Boolean, val hardest: QueensRung?, val steps: Int)
