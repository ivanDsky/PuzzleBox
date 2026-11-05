package com.ivandsky.puzzlebox.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.playTarget
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything Home renders: today's set, the signed-in player, the history teaser, and
 * [puzzleDay] — the puzzle roll-over day (A9) the header shows, not the phone's date.
 * [playTarget] is the game "Play today" opens (core:domain's `playTarget`: the first game
 * with an unfinished save, else the first unsolved one, else the first game; before the
 * set lands, the first game) — and [resume] says the first applies, so the action reads
 * "Resume".
 */
data class HomeUiState(
    val today: List<TodayRow>,
    val identity: Identity?,
    val history: List<HistoryDay>,
    val puzzleDay: LocalDate,
    val playTarget: GameId?,
) {
    /** One game in the Today card (A15); [status] is null until today's set first emits. */
    data class TodayRow(val game: GameId, val displayName: String, val status: TodayStatus?)

    val resume: Boolean
        get() = today.any { it.status is TodayStatus.InProgress }
}

/**
 * Home's state holder. The Today card is [ObserveTodaySet] (A15): every registered game in
 * registry order with its status, seeded from [registry] so the first frame already lists
 * every game. The teaser observes the GAME-AGNOSTIC merged history ([ObserveGameHistory] —
 * every game's completion records UNION every registered game's captured days; fix
 * round 5, defect C) and groups it by day: the newest [RECENT_DAYS] days before the
 * puzzle day (today lives in the Today card), each listing its games in registry order
 * (unregistered games last) with their solve time, or null when opened but not solved.
 * Per-game names resolve against :app's [GameRegistry], so a new game appears here purely
 * by registering its plugin. The header's day is [puzzleDay]'s result, re-read on every
 * emission. [syncToday] prefetches today's uncached puzzles (A12; the route calls it on
 * every resume), so a game that can't load reads Offline.
 */
class HomeViewModel(
    observeGameHistory: ObserveGameHistory,
    todaySet: ObserveTodaySet,
    identityService: IdentityService,
    private val registry: GameRegistry,
    private val puzzleDay: GetPuzzleDay,
    private val syncTodayPuzzles: SyncTodayPuzzles,
) : ViewModel() {

    private val seed = registry.all.map {
        HomeUiState.TodayRow(it.definition.id, it.definition.displayName, status = null)
    }

    // Before the set lands every row is unknown and "Play today" opens the first game.
    private val seedTarget = seed.firstOrNull()?.game

    // Seeded (null) so the teaser never waits on today's set (it reads Room and DataStore).
    private val today: Flow<List<TodayEntry>?> = todaySet().onStart<List<TodayEntry>?> { emit(null) }

    val uiState: StateFlow<HomeUiState> =
        combine(today, observeGameHistory(), identityService.current) { entries, days, identity ->
            val day = puzzleDay()
            HomeUiState(
                today = entries?.map { HomeUiState.TodayRow(it.game, it.displayName, it.status) } ?: seed,
                identity = identity,
                history = recentDays(days, before = day),
                puzzleDay = day,
                playTarget = if (entries == null) seedTarget else entries.playTarget(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(seed, identityService.current.value, emptyList(), puzzleDay(), seedTarget),
        )

    fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    private fun recentDays(days: List<GameHistoryDay>, before: LocalDate): List<HistoryDay> =
        days.asSequence()
            .filter { it.day < before }
            .groupBy { it.day }
            .entries
            .sortedByDescending { it.key }
            .take(RECENT_DAYS)
            .map { (day, games) ->
                HistoryDay(
                    isoDay = day.toString(),
                    headline = day.format(HEADLINE),
                    games = games
                        .sortedWith(compareBy({ registryRank(it.gameId) }, { it.gameId.value }))
                        .map { game ->
                            HistoryDay.GameResult(
                                gameName = registry.displayName(game.gameId),
                                time = game.record?.let { formatDuration(it.time) },
                            )
                        },
                )
            }

    private fun registryRank(id: GameId): Int =
        registry.all.indexOfFirst { it.definition.id == id }.let { if (it < 0) Int.MAX_VALUE else it }

    private companion object {
        const val RECENT_DAYS = 3
        val HEADLINE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")
    }
}
