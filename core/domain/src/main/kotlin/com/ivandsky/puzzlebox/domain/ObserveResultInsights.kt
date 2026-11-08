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
 * back as today's solve. The numbers are the same before and after the write lands.
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
     * numbered daily and false for a replay: a replay keeps the streak and week as the history
     * stands. A practice puzzle (its history row has no edition number) emits null.
     */
    operator fun invoke(game: GameId, day: LocalDate, solve: BestScore, counted: Boolean): Flow<ResultInsights?> {
        val order = registry[game]?.score?.rankOrder ?: RankOrder.TimeThenHints
        return history().map { days ->
            val own = days.firstOrNull { it.gameId == game && it.day == day }
            if (own != null && own.puzzleNumber == null) return@map null

            val rest = days.filterNot { it === own }
            val earlier = rest
                .filter { it.gameId == game && it.isSolvedDaily() }
                .mapNotNull { row -> row.record?.let { BestScore(it.time, it.hints) } }
            val streakDays = rest.filter { it.isStreakDay() }.mapTo(mutableSetOf()) { it.day }
            if (counted) streakDays += day else own?.takeIf { it.isStreakDay() }?.let { streakDays += it.day }

            val today = puzzleDay()
            ResultInsights(
                streak = computeStreak(streakDays),
                week = (WEEK_DAYS - 1 downTo 0).map { ago ->
                    val date = today.minusDays(ago.toLong())
                    ResultDay(date, solved = date in streakDays, today = date == today)
                },
                spread = timeSpread(earlier, solve, order),
            )
        }.distinctUntilChanged()
    }
}
