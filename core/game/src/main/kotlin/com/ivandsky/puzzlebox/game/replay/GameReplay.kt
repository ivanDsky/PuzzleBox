package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/**
 * Game adapter for solve replay (ADR-0008). Pure and deterministic: no clock. [step] runs no solver or hint
 * engine, so a replay never re-runs what the live play already decided. [factsOf] does its puzzle-wide work
 * (the solution a compare counts against) once per timeline. [markOf] may run the live hint engine: Tango and
 * Queens recompute the target of each recorded hint from the board before it, at open.
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
     * (any move, kept or not), when it shares [move]'s stamp; null otherwise, and for the first move.
     * Game-owned indices in [MoveMark.hinted]. Pure.
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

    /**
     * Whether [move] is the next step of the tap cycle on one cell that [previous] belonged to: the
     * player tapped that cell again before the board settled. [previous] is a move of the stamp group
     * that came less than [Timeline.MISTAKE_SETTLE_MS] before [move]'s group, tried last first, and [move]
     * is the first move of the later group. True = the timeline folds the earlier group into the later
     * one, so one tap cycle is one tick and no frame shows the half-way board. Pure; the default never merges.
     */
    fun continuesTap(previous: ReplayMove, move: ReplayMove): Boolean = false
}
