package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.model.GameId

/**
 * What an entry tile says about one period: [solved] counted solves, [byGame] how many of them each game has (most
 * first, ties by game id), and for a year [months], the count of each of its twelve months (empty for a month).
 */
internal class RecapSummary(val solved: Int, val byGame: List<Pair<GameId, Int>>, val months: List<Int>)

/** A recap that is due on Today with its [summary]. */
internal class DuePeriod(val period: RecapPeriod, val summary: RecapSummary)

/** The [period]'s summary from the [counted] history Today already reads: a group-by, no second read. */
internal fun summarize(period: RecapPeriod, counted: List<GameHistoryDay>): RecapSummary {
    val inPeriod = counted.filter { it.day in period }
    val byGame = inPeriod.groupingBy { it.gameId }.eachCount().toList()
        .sortedWith(compareByDescending<Pair<GameId, Int>> { it.second }.thenBy { it.first.value })
    val months = if (period is RecapPeriod.Year) {
        val perMonth = inPeriod.groupingBy { it.day.monthValue }.eachCount()
        List(MONTHS) { perMonth[it + 1] ?: 0 }
    } else {
        emptyList()
    }
    return RecapSummary(inPeriod.size, byGame, months)
}

private const val MONTHS = 12
