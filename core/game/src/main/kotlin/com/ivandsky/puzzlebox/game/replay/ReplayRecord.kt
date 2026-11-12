package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.model.GameId

/** A decoded replay: the game, how the solve ended, its end time and the stamped moves. */
data class ReplayRecord(val game: GameId, val outcome: ReplayOutcome, val endMs: Long, val moves: List<StampedMove>)
