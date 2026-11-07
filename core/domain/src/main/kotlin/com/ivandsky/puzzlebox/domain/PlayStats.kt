package com.ivandsky.puzzlebox.domain

import java.time.LocalDate

/**
 * What the tablet play side panel knows about one slot (A15), from [ObservePlayStats]:
 * today's set progress ([solvedToday] of [gamesToday]), the slot game's [stats] and its
 * [streakDays], the game's [today], its [board] for the slot's day, and whether the slot
 * itself is [solved] (its time is then posted, or on its way to the board).
 */
data class PlayStats(
    val solvedToday: Int,
    val gamesToday: Int,
    val stats: GameStats,
    /** This game's days that move its streak: numbered dailies, never archive plays (A23). */
    val streakDays: Set<LocalDate>,
    val today: LocalDate,
    val board: TodayLeaderboard,
    val solved: Boolean,
)
