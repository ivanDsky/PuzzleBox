package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.model.GameId

/**
 * What a self duel asks for ([CreateSelfDuel]): at most [rounds] solved puzzles of [games], friends' puzzles too when
 * [includeFriends], or the [lineup] when set.
 */
data class SelfDuelSettings(
    val rounds: Int,
    val games: Set<GameId>,
    val includeFriends: Boolean,
    /** Non-null: the duel's puzzles, in order (1..Match.MAX_LEGS); [rounds] and [games] are then ignored. */
    val lineup: List<LineupEntry>? = null,
)
