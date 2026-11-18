package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * What the New duel sheet asks for ([CreateMatch]): at most [count] puzzles from [games] (today's alone with
 * [todayOnly]), friends' puzzles too when [includeFriends], the [pastedCodes] first, and [deadlineHours] to play.
 * A non-null [lineup] wins: the duel's legs follow it exactly and [count], [games], [todayOnly] and [pastedCodes]
 * are ignored.
 */
data class DuelSettings(
    val count: Int,
    val games: Set<GameId>,
    val todayOnly: Boolean,
    val deadlineHours: Int,
    val includeFriends: Boolean,
    val pastedCodes: List<ShareCode>,
    val lineup: List<LineupEntry>? = null,
)
