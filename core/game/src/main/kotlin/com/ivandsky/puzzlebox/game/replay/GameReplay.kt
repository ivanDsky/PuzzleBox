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

    /**
     * How the timeline and the board mark one move. [previous] = the move decoded just before [move]
     * (any move, kept or not); null for the first. Game-owned indices in [MoveMark.hinted]. Pure.
     */
    fun markOf(before: S, move: ReplayMove, after: S, previous: ReplayMove?): MoveMark = MoveMark.Plain

    /**
     * Whether a Mistake the player made was transient: every violation that [after] newly introduced
     * (against [before]) is gone in [next], the board after the very next stamp group, which landed
     * less than [Timeline.MISTAKE_SETTLE_MS] later. True = the live game never showed it, so the timeline
     * does not mark it. Called only for a group whose [markOf] kind is Mistake. Pure and cheap; the default
     * never downgrades.
     */
    fun isTransientMistake(before: S, after: S, next: S): Boolean = false
}
