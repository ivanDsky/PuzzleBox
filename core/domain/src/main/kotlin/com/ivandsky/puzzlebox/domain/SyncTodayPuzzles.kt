package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Prefetches today's puzzles (A12): every registered game whose puzzle for its calendar
 * day is not cached yet is fetched, in registry order. [failed] holds the games whose
 * last fetch found nothing, so [ObserveTodaySet] can read them [TodayStatus.Offline]; a
 * cached or fetched game leaves it. One instance app-wide (a Koin single), so Home's and
 * the hub's syncs share it.
 */
class SyncTodayPuzzles(
    private val registry: GameRegistry,
    private val daily: DailyRepository,
    private val calendar: GameCalendar,
) {

    private val failures = MutableStateFlow<Set<GameId>>(emptySet())

    val failed: StateFlow<Set<GameId>> = failures.asStateFlow()

    suspend operator fun invoke() {
        for (plugin in registry.all) {
            val game = plugin.definition.id
            val day = calendar.today(game) ?: continue
            val ready = daily.cached(game, day) != null || daily.today(game) !is DailyToday.Unavailable
            failures.update { if (ready) it - game else it + game }
        }
    }
}
