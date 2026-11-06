package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.GameSession

/**
 * In-progress play (A30): the rules [state] and the banked play time. [ref] is null for a
 * board without daily identity, which is never record-eligible. The live trace is not here:
 * an uncommitted trace is never persisted.
 */
data class StrandsSession(
    val ref: StrandsPuzzleRef?,
    val state: StrandsState,
    val elapsedMs: Long = 0L,
) : GameSession
