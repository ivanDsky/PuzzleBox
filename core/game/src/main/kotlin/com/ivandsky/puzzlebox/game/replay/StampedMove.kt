package com.ivandsky.puzzlebox.game.replay

/** One move with its play-time stamp. */
data class StampedMove(val atMs: Long, val move: ReplayMove)
