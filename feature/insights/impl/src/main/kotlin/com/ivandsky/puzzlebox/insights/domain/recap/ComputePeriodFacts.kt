package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.domain.isStreakDay
import com.ivandsky.puzzlebox.domain.moments.detectMoments
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import java.time.LocalDate

/**
 * Folds the full game history ([com.ivandsky.puzzlebox.domain.ObserveGameHistory]) into one period's [PeriodFacts]. It
 * takes the whole history and filters the period on output, because a streak needs the days before the period too: the
 * streak walk ([ComputeStreak], the game page's own) runs over the whole history and [StreakRuns] cuts the period out.
 *
 * A solve counts through [isSolvedDaily], the predicate the game page's stats block counts with, so a recap and that
 * block never disagree: the day's record row of a numbered daily, in-app or imported. Play again, practice, custom
 * and duel-only solves have no such row. A solve belongs to the period of its puzzle day, so an old puzzle imported
 * today lands in its own year. A streak day is a counted solve through [isStreakDay], again the game page's rule.
 * Hint-free and total time come off the same counted solves; the Moments come from [ReplaySolveFacts] over the whole
 * history and the one Moment detector.
 */
internal class ComputePeriodFacts(
    private val computeStreak: ComputeStreak,
    private val replaySolveFacts: ReplaySolveFacts,
    private val registry: GameRegistry,
) {

    operator fun invoke(history: List<GameHistoryDay>, period: DaySpan, scope: GameId? = null): PeriodFacts {
        val inScope = history.filter { scope == null || it.gameId == scope }
        val counted = inScope.filter { it.isSolvedDaily() && it.day in period }
        val streakDays = inScope.filter { it.isStreakDay() }.map { it.day }
        val streaks = StreakRuns.of(streakDays, computeStreak(streakDays).freezes).within(period)
        val longestStreak = streaks.maxByOrNull { it.days }
        return PeriodFacts(
            period = period,
            scope = scope,
            solved = counted.size,
            solvedByGame = counted.groupingBy { it.gameId }.eachCount(),
            fastestByGame = counted.groupBy { it.gameId }.mapValues { (_, days) -> fastest(days) },
            averageTimeMsByGame = counted.groupBy { it.gameId }.mapValues { (_, days) -> days.sumOf { it.solve.time } / days.size },
            longestStreak = longestStreak,
            longestStreakSolve = longestStreak?.let { lastSolveOf(it, counted) },
            freezesSpent = streaks.sumOf { it.frozen.size },
            hintFree = counted.count { it.solve.hints == 0 },
            longestHintFreeRun = HintFreeRun.longestOf(counted, registry.all.map { it.definition.id }),
            totalTimeMs = counted.sumOf { it.solve.time },
            moments = momentsOf(history, period, scope),
            months = if (period is RecapPeriod.Year) monthsOf(counted) else emptyList(),
            importedDays = importedDaysOf(counted, period),
            dayTallies = counted.groupBy { it.day }.toSortedMap().map { (day, solves) -> tally(day, solves) },
        )
    }

    /** One [MonthFacts] per month of the year's [counted] solves, January first; the quickest is by time, then fewer hints, then the earlier day. */
    private fun monthsOf(counted: List<GameHistoryDay>): List<MonthFacts> =
        counted.groupBy { YearMonth.from(it.day) }.toSortedMap().map { (month, days) ->
            val best = days.minWith(compareBy<GameHistoryDay>({ it.solve.time }, { it.solve.hints }, { it.day }))
            MonthFacts(month = month, solved = days.size, fastestGame = best.gameId, fastestTimeMs = best.solve.time)
        }

    /** The puzzle days a year of only imported solves covers; null when [period] is a month, or any counted solve was made in the app, or none was. */
    private fun importedDaysOf(counted: List<GameHistoryDay>, period: DaySpan): Int? =
        counted.takeIf { period is RecapPeriod.Year && it.isNotEmpty() && it.all { day -> day.solve.source == CompletionSource.LinkedIn } }
            ?.distinctBy { it.day }?.size

    /**
     * The Moments of the period's solves, from the replay of the whole history: all games feed a streak or a set, and
     * only then is the scope applied to what is read out.
     */
    private fun momentsOf(history: List<GameHistoryDay>, period: DaySpan, scope: GameId?): List<DatedMoment> =
        replaySolveFacts(history)
            .filter { it.day in period && (scope == null || it.game == scope) }
            .sortedBy { it.day }
            .flatMap { solve -> detectMoments(solve.facts).map { DatedMoment(it, solve.day, solve.game) } }

    /** The quickest of the counted solves of [run]'s last day that has one: a frozen day has none, and a streak day beyond the scope's counted solves is skipped. */
    private fun lastSolveOf(run: StreakRun, counted: List<GameHistoryDay>): SolveRef? {
        val last = counted.filter { it.day in run.start..run.end }.maxOfOrNull { it.day } ?: return null
        val quickest = counted.filter { it.day == last }.minWith(compareBy<GameHistoryDay>({ it.solve.time }, { it.gameId.value }))
        return SolveRef.of(quickest)
    }

    private fun tally(day: LocalDate, solves: List<GameHistoryDay>) = DayTally(
        day = day,
        solved = solves.size,
        timeMs = solves.sumOf { it.solve.time },
        hintFree = solves.count { it.solve.hints == 0 },
    )

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
