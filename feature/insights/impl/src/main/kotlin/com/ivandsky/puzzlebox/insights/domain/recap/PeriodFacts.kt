package com.ivandsky.puzzlebox.insights.domain.recap

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
)
