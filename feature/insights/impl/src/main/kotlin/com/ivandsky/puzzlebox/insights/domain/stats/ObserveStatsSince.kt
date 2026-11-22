package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * The dates the stats page's Since filter words its rows with: the puzzle day of [calendar] (the scope game's, or the first
 * registered game's for every game, as [ObserveStatsPage] reads it) and the day of the first counted solve in the scope.
 * With no game registered there is no puzzle day, so it emits null once and the page reaches its empty state.
 */
internal class ObserveStatsSince(
    private val observeGameHistory: ObserveGameHistory,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
) {

    operator fun invoke(scope: GameId?): Flow<StatsSince?> {
        val clockGame = scope ?: registry.all.firstOrNull()?.definition?.id ?: return flowOf(null)
        return combine(observeGameHistory(), calendar.observeToday(clockGame)) { history, today ->
            StatsSince(today, history.filter { it.isSolvedDaily() && (scope == null || it.gameId == scope) }.minOfOrNull { it.day })
        }.distinctUntilChanged()
    }
}
