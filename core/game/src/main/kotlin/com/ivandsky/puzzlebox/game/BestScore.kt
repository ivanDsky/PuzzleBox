package com.ivandsky.puzzlebox.game

/** A game's best first solve: its time and hints, ranked by the game's [RankOrder] (ADR-0013). */
data class BestScore(val timeMs: Long, val hints: Int)
