package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth

/**
 * One month of a year's counted solves: how many there were, the quickest of them in any game with the game it was in,
 * and the month's best against the player's own average: the game whose best time that month beat its year average by
 * the most, with that time and the [underAveragePercent] it beat it by (0 when it only matched it). A month with no
 * counted solve has no [MonthFacts].
 */
internal data class MonthFacts(
    val month: YearMonth,
    val solved: Int,
    val fastestGame: GameId,
    val fastestTimeMs: Long,
    val bestGame: GameId,
    val bestTimeMs: Long,
    val underAveragePercent: Int,
)
