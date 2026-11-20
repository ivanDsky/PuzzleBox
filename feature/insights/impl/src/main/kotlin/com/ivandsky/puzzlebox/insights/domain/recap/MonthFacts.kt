package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth

/**
 * One month of a year's counted solves: how many there were, the quickest of them in any game with the game it was in,
 * and the month's best against the player's own average: the game whose best time that month beat its year average by
 * the most, with that time and the [underAveragePercent] it beat it by (0 when it only matched it). [byGame] has every
 * game played that month, which the month card's headline is picked from. A month with no counted solve has no
 * [MonthFacts].
 */
internal data class MonthFacts(
    val month: YearMonth,
    val solved: Int,
    val fastestGame: GameId,
    val fastestTimeMs: Long,
    val bestGame: GameId,
    val bestTimeMs: Long,
    val underAveragePercent: Int,
    val byGame: Map<GameId, MonthBest>,
) {

    /**
     * One game's month: its quickest counted solve that month, [timeMs] on [day] (by time, then fewer hints, then the
     * earlier day), beside [averageMs], the floor of its mean counted time over the year (at least 1). [monthSolves] of
     * its [yearSolves] counted solves in the year fell in the month.
     */
    data class MonthBest(val day: LocalDate, val timeMs: Long, val averageMs: Long, val monthSolves: Int, val yearSolves: Int) {
        /** Whole percent below the average, rounded; 0 when at or above it. */
        val underAveragePercent: Int get() = if (timeMs >= averageMs) 0 else (((averageMs - timeMs) * 100 + averageMs / 2) / averageMs).toInt()
    }
}
