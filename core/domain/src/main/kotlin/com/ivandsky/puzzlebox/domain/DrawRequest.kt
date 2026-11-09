package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * What a new duel's draw ([ComputeMatchLegs]) asks for: at most [count] puzzles from [games]' dailies
 * (today's alone with [todayOnly]), plus friends' puzzles when [includeFriends], after the [pastedCodes].
 * [me] is the drawing player's uid, so their own puzzles never come back.
 */
data class DrawRequest(
    val count: Int,
    val games: Set<GameId>,
    val todayOnly: Boolean,
    val includeFriends: Boolean,
    val pastedCodes: List<ShareCode>,
    val me: String,
)
