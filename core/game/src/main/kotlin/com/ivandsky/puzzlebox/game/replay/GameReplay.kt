package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/**
 * Game adapter for solve replay (ADR-0008). Pure and deterministic: no clock, and no solver or hint
 * engine inside [step], so a replay never re-runs what the live play already decided.
 */
interface GameReplay<S : GameSession> {
    /** Same instance back = the move changed nothing. Null = the move is not understood. Never throws. */
    fun step(session: S, move: ReplayMove, atMs: Long): S?

    /**
     * Prepares the facts function for one timeline. Called once per timeline open; puzzle-wide work
     * (the solution) is done here and lives in the closure, so the adapter holds no cache.
     */
    fun factsOf(initial: S): (S) -> ReplayFacts
}
