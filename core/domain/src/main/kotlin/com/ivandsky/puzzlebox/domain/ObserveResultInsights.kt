package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Derives [ResultInsights] for one solve from the merged history ([ObserveGameHistory]).
 *
 * The solve's own record write is asynchronous, so the history may or may not hold it yet:
 * the slot's own row is always left out of the earlier data, and a counted solve is added
 * back as today's solve. The numbers are the same before and after the write lands, the freeze
 * moment included: it is shown only for the first counted solve of the day, so a second game's
 * solve of a day another game already solved, never repeats it.
 * Archived days (A23) count in the spread but never in the streak or week, as in
 * [ObserveGameStats].
 */
class ObserveResultInsights(
    private val registry: GameRegistry,
    private val history: ObserveGameHistory,
    private val computeStreak: ComputeStreak,
    private val puzzleDay: GetPuzzleDay,
) {

    /**
     * [solve] is the player's time and hints for [game]'s [day]. [counted] is true for today's
     * numbered daily and false for a play-again: a play-again keeps the streak and week as the history
     * stands. A practice puzzle (its history row has no edition number) emits null.
     */
    operator fun invoke(game: GameId, day: LocalDate, solve: BestScore, counted: Boolean): Flow<ResultInsights?> {
        val order = registry[game]?.score?.rankOrder ?: RankOrder.TimeThenHints
        return history().map { days ->
            val own = days.firstOrNull { it.gameId == game && it.day == day }
            if (own != null && own.puzzleNumber == null) return@map null

            val rest = days.filterNot { it === own }
            val earlierRows = rest.filter { it.gameId == game && it.isSolvedDaily() }
            val earlier = earlierRows.mapNotNull { row -> row.record?.let { row.day to BestScore(it.time, it.hints) } }
            val restStreakDays = rest.filter { it.isStreakDay() }.mapTo(mutableSetOf()) { it.day }
            val streakDays = restStreakDays.toMutableSet()
            if (counted) streakDays += day else own?.takeIf { it.isStreakDay() }?.let { streakDays += it.day }

            val today = puzzleDay()
            val ledger = computeStreak(streakDays)
            val freezes = ledger.freezes
            val moment = freezes?.lastSolve?.takeIf {
                counted && it.day == day && day !in restStreakDays && (it.spent.isNotEmpty() || it.earned)
            }
            ResultInsights(
                streak = ledger.current,
                week = (WEEK_DAYS - 1 downTo 0).map { ago ->
                    val date = today.minusDays(ago.toLong())
                    ResultDay(date, solved = date in streakDays, today = date == today, frozen = freezes != null && date in freezes.frozenDays)
                },
                spread = timeSpread(earlier.map { it.second }, solve, order),
                weekday = weekdaySpread(earlier, day, solve, order),
                freezes = freezes?.copy(lastSolve = moment),
            )
        }.distinctUntilChanged()
    }
}

private fun weekdaySpread(earlier: List<Pair<LocalDate, BestScore>>, day: LocalDate, solve: BestScore, order: RankOrder): WeekdaySpread {
    val byWeekday = earlier.groupBy({ it.first.dayOfWeek }, { it.second })
    val mine = byWeekday[day.dayOfWeek].orEmpty()
    val averages = byWeekday.filterValues { it.size >= WEEKDAY_MIN_EARLIER }.mapValues { (_, times) -> times.sumOf { it.timeMs } / times.size }
    val own = averages[day.dayOfWeek]
    val fastest = own != null && averages.size > 1 && averages.all { (weekday, average) -> weekday == day.dayOfWeek || average > own }
    return WeekdaySpread(day.dayOfWeek, timeSpread(mine, solve, order), fastest)
}
