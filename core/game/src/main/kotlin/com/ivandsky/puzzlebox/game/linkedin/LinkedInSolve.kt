package com.ivandsky.puzzlebox.game.linkedin

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.replay.MoveKind

/**
 * A typed solve handed to a game's LinkedIn encoder: the session after each timeline tick, in order
 * (last = the solved board), and each tick's kind and play-time stamp in ms, index-aligned. Order-based bindings (Tango, Queens
 * solve order) and undo/mistake facts come from here, so game N+1 adds an encoder, not a seam change.
 */
class LinkedInSolve<S : GameSession>(val boards: List<S>, val kinds: List<MoveKind>, val timesMs: List<Long>) {
    val final: S get() = boards.last()

    /** No rejected move, hint or undo anywhere in the solve: the part of A13's flawless rule every game shares. */
    val mistakeFree: Boolean get() = kinds.none { it in FLAWS }

    private companion object {
        val FLAWS = setOf(MoveKind.Mistake, MoveKind.Hint, MoveKind.Undo)
    }
}
