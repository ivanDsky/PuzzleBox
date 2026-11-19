package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.domain.isStreakDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.model.GameId

/**
 * Folds the full game history ([com.ivandsky.puzzlebox.domain.ObserveGameHistory]) into one period's [PeriodFacts]. It
 * takes the whole history and filters the period on output, because a streak needs the days before the period too: the
 * streak walk ([ComputeStreak], the game page's own) runs over the whole history and [StreakRuns] cuts the period out.
 *
 * A solve counts through [isSolvedDaily], the predicate the game page's stats block counts with, so a recap and that
 * block never disagree: the day's record row of a numbered daily, in-app or imported. Play again, practice, custom
 * and duel-only solves have no such row. A solve belongs to the period of its puzzle day, so an old puzzle imported
 * today lands in its own year. A streak day is a counted solve through [isStreakDay], again the game page's rule.
 */
internal class ComputePeriodFacts(private val computeStreak: ComputeStreak) {

    operator fun invoke(history: List<GameHistoryDay>, period: RecapPeriod, scope: GameId? = null): PeriodFacts {
        val inScope = history.filter { scope == null || it.gameId == scope }
        val counted = inScope.filter { it.isSolvedDaily() && it.day in period }
        val streakDays = inScope.filter { it.isStreakDay() }.map { it.day }
        val streaks = StreakRuns.of(streakDays, computeStreak(streakDays).freezes).within(period)
        return PeriodFacts(
            period = period,
            scope = scope,
            solved = counted.size,
            solvedByGame = counted.groupingBy { it.gameId }.eachCount(),
            fastestByGame = counted.groupBy { it.gameId }.mapValues { (_, days) -> fastest(days) },
            longestStreak = streaks.maxByOrNull { it.days },
            freezesSpent = streaks.sumOf { it.frozen.size },
        )
    }

    private fun fastest(days: List<GameHistoryDay>): FastestSolve {
        val best = days.minWith(compareBy<GameHistoryDay>({ it.solve.time }, { it.solve.hints }, { it.day }))
        return FastestSolve(
            day = best.day,
            puzzleNumber = best.puzzleNumber,
            timeMs = best.solve.time,
            hasReplay = best.solve.source != CompletionSource.LinkedIn,
        )
    }

    /** The record row of a counted day: [isSolvedDaily] has already said there is one. */
    private val GameHistoryDay.solve: CompletionRecord get() = checkNotNull(record)
}
