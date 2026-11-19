package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.domain.TodayLeaderboard

/**
 * What the tablet play side panel knows about one slot (A15), from [ObservePlayStats]:
 * today's set progress ([solvedToday] of [gamesToday]), its [board] for the slot's day, and
 * whether the slot itself is [solved] (its time is then posted, or on its way to the board).
 */
data class PlayStats(
    val solvedToday: Int,
    val gamesToday: Int,
    /** Null for a custom puzzle, which has no daily board. */
    val board: TodayLeaderboard?,
    val solved: Boolean,
)
