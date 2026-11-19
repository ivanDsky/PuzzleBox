package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.model.GameId

/**
 * Folds the full game history ([com.ivandsky.puzzlebox.domain.ObserveGameHistory]) into one period's [PeriodFacts]. It
 * takes the whole history and filters the period on output, because the facts later cards add (streak, freezes,
 * Moments) need the days before the period too.
 *
 * A solve counts through [isSolvedDaily], the predicate the game page's stats block counts with, so a recap and that
 * block never disagree: the day's record row of a numbered daily, in-app or imported. Play again, practice, custom
 * and duel-only solves have no such row. A solve belongs to the period of its puzzle day, so an old puzzle imported
 * today lands in its own year.
 */
internal class ComputePeriodFacts {

    operator fun invoke(history: List<GameHistoryDay>, period: RecapPeriod, scope: GameId? = null): PeriodFacts {
        val counted = history.filter { it.isSolvedDaily() && it.day in period && (scope == null || it.gameId == scope) }
        return PeriodFacts(
            period = period,
            scope = scope,
            solved = counted.size,
            solvedByGame = counted.groupingBy { it.gameId }.eachCount(),
        )
    }
}
