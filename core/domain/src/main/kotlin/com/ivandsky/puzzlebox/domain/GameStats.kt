package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate

/**
 * One player's daily-puzzle numbers ([ObserveGameStats]), for one game or all of them.
 * Times are solve times in ms; the time fields and [playingSince] are null until the
 * first solve.
 */
data class GameStats(
    /** The solved-day streak ([ComputeStreak]). */
    val currentStreak: Int,
    /** The longest run of solved days ever ([ComputeStreak.best]). */
    val bestStreak: Int,
    /** Solved dailies, one per game and day. */
    val solved: Int,
    val bestTimeMs: Long?,
    /** Solved dailies that used no hint. */
    val hintFree: Int,
    val averageTimeMs: Long?,
    /** The first solved day. */
    val playingSince: LocalDate?,
    /** The best solved daily under [rankOrder]; null until the first solve. */
    val best: BestScore? = null,
    /** The game's ranking; [RankOrder.TimeThenHints] for every game's stats. */
    val rankOrder: RankOrder = RankOrder.TimeThenHints,
)
