package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.WeekDay
import java.time.LocalDate

private const val WEEK_DAYS = 7L

/** The 7 puzzle days ending on [endingOn], oldest first, each with its solved dailies' brands. */
internal fun homeWeek(
    days: List<GameHistoryDay>,
    endingOn: LocalDate,
    registryOrder: Comparator<GameHistoryDay>,
    brands: GamePlayRegistry,
): List<WeekDay> {
    val solved = days
        .filter { it.record != null && it.puzzleNumber != null && it.day > endingOn.minusDays(WEEK_DAYS) }
        .sortedWith(registryOrder)
        .groupBy({ it.day }, { it.gameId })
    return (WEEK_DAYS - 1 downTo 0L).map { back ->
        val date = endingOn.minusDays(back)
        WeekDay(
            date = date,
            solved = solved[date].orEmpty().distinct().map(brands::brandOf),
            isToday = back == 0L,
        )
    }
}
