package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth

/**
 * One month of a year's counted solves: how many there were, and the quickest of them in any game, with the game it
 * was in. A month with no counted solve has no [MonthFacts].
 */
internal data class MonthFacts(
    val month: YearMonth,
    val solved: Int,
    val fastestGame: GameId,
    val fastestTimeMs: Long,
)
