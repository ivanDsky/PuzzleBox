package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.model.GameId

/**
 * The facts of one [period] for one [scope]: one game, or every game when null (the same scope the game page's stats
 * take). Only counted solves feed it (A1). Later recap cards add their facts here.
 */
internal data class PeriodFacts(
    val period: RecapPeriod,
    val scope: GameId?,
    /** Counted solves in the period: one per game and puzzle day, imports included (A5). */
    val solved: Int,
    /** [solved] per game; a game with no solve in the period has no entry. */
    val solvedByGame: Map<GameId, Int>,
    /** Each game's quickest counted solve in the period (time, then fewer hints, then the earlier day); a game with no solve has no entry. */
    val fastestByGame: Map<GameId, FastestSolve>,
    /**
     * The longest streak's part inside the period, frozen days included; null when no streak day falls in it. A streak
     * across the period's edge counts only the days on this side of it.
     */
    val longestStreak: StreakRun?,
    /** The days a freeze held a streak inside the period, over every streak that reaches it. */
    val freezesSpent: Int,
    /** Counted solves in the period with no hint; an import carries its hint as 0 or 1, so it counts like any solve. */
    val hintFree: Int,
    /** The play time of the period's counted solves, pauses and background left out; an import adds its LinkedIn time (A8). */
    val totalTimeMs: Long,
    /**
     * The Moments the period's solves earned, oldest day first. Friends are absent, so only the local kinds appear:
     * personal best, first sub-X, full set and streak (A6). They are read off the full history, so a personal best in
     * month 2 is relative to month 1.
     */
    val moments: List<Moment>,
)
