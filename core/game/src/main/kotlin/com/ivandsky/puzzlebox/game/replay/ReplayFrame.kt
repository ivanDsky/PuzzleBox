package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/** The board of one replay at one play time. */
class ReplayFrame(
    /** Session after the last move stamped <= t. */
    val session: GameSession,
    /**
     * Session before the FIRST move that shares that move's stamp (live play logs a rule-breaking
     * placement as two moves at one stamp); null before the first move. Forced by A6 (a new
     * violation settles for 500 ms, so the previous board must stay reachable); only Tango reads it.
     */
    val before: GameSession?,
    /** Union of [MoveMark.hinted] over the applied moves. The board marks only cells still holding the hinted content. */
    val hinted: Set<Int>,
    val facts: ReplayFacts,
    /** t, clamped to 0..endMs. */
    val atMs: Long,
    /**
     * t minus the stamp of that move; t itself before the first move. Forced by A6; only Tango
     * reads it.
     */
    val sinceMoveMs: Long,
    /** t >= endMs: the frame is frozen. */
    val ended: Boolean,
)
