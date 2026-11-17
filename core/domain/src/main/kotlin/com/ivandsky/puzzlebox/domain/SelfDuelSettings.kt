package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId

/** What a self duel asks for ([CreateSelfDuel]): at most [rounds] solved puzzles of [games], friends' puzzles too when [includeFriends]. */
data class SelfDuelSettings(
    val rounds: Int,
    val games: Set<GameId>,
    val includeFriends: Boolean,
)
