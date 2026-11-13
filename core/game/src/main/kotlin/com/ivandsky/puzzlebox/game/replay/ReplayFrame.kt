package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/** The board of one replay at one play time. */
class ReplayFrame(
    /** Session after the last move stamped <= t. */
    val session: GameSession,
    /**
     * Session before the FIRST move that shares the stamp of the last applied move; null before the first
     * move. A stamp group is what live play did in one instant (a rule-breaking placement is two moves at one
     * stamp), and a tap cycle the timeline merged ([GameReplay.continuesTap]) is one group, so this is the board
     * before the whole cycle. Tango reads it to tell a violation new in this group (hidden until it settles)
     * from one already there; Strands reads it to see which word the group found or submitted
     * (the settle or link sweep, the word slot, the set-aside path).
     */
    val before: GameSession?,
    /** Union of [MoveMark.hinted] over the applied moves. The board marks only cells still holding the hinted content. */
    val hinted: Set<Int>,
    val facts: ReplayFacts,
    /** t, clamped to 0..endMs. */
    val atMs: Long,
    /**
     * t minus the stamp of the last applied move (the group's stamp, the later one for a merged tap
     * cycle); t itself before the first move. Only Tango reads it: the age of the group against
     * `MotionTokens.SettleMillis` decides whether a new violation shows yet.
     */
    val sinceMoveMs: Long,
    /** t >= endMs: the frame is frozen. */
    val ended: Boolean,
)
