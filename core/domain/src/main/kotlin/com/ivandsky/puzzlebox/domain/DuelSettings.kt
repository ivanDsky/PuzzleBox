package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * What the New duel sheet asks for ([CreateMatch]): at most [count] puzzles from [games] (today's alone with
 * [todayOnly]), friends' puzzles too when [includeFriends], the [pastedCodes] first, and [deadlineHours] to play.
 */
data class DuelSettings(
    val count: Int,
    val games: Set<GameId>,
    val todayOnly: Boolean,
    val deadlineHours: Int,
    val includeFriends: Boolean,
    val pastedCodes: List<ShareCode>,
)
