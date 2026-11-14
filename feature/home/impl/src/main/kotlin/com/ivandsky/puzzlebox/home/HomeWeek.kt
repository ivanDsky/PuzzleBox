package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.weekEnding
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.WeekDay
import java.time.LocalDate

/**
 * The 7 puzzle days ending on [endingOn], oldest first, each with its solved dailies' brands
 * and whether a freeze covers it ([frozenDays]). The rings count archive plays too (A23),
 * which the streak itself does not, so a frozen day can still be ringed.
 */
internal fun homeWeek(
    days: List<GameHistoryDay>,
    endingOn: LocalDate,
    frozenDays: Set<LocalDate>,
    registryOrder: Comparator<GameHistoryDay>,
    brands: GamePlayRegistry,
): List<WeekDay> {
    val week = weekEnding(endingOn)
    val solved = days
        .filter { it.record != null && it.puzzleNumber != null && it.day >= week.first() }
        .sortedWith(registryOrder)
        .groupBy({ it.day }, { it.gameId })
    return week.map { date ->
        WeekDay(
            date = date,
            solved = solved[date].orEmpty().distinct().map(brands::brandOf),
            isToday = date == endingOn,
            frozen = date in frozenDays,
        )
    }
}
