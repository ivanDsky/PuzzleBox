package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.solvedByDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The calendar over local [history] alone: it renders signed out and offline, and never waits on the network. */
internal fun calendarStateOf(
    history: Flow<List<HistoryDayRow>>,
    selection: Flow<RanksSelection>,
    today: Flow<LocalDate>,
    games: List<GameId>,
): Flow<CalendarState> = combine(
    history,
    selection.map { Triple(it.day, it.shownDay, it.calendarExpanded) }.distinctUntilChanged(),
    today,
) { rows, (picked, shown, expanded), today ->
    val earliest = rows.minOfOrNull { it.day }?.coerceAtMost(today) ?: today
    CalendarState(
        shownDay = (shown ?: picked ?: today).coerceIn(earliest, today),
        expanded = expanded,
        earliestDay = earliest,
        selectedDay = picked ?: today,
        solvedByDay = solvedByDay(rows, games),
        daysWithRows = rows.mapTo(HashSet()) { it.day },
        gamesPerDay = games.size,
        today = today,
    )
}
