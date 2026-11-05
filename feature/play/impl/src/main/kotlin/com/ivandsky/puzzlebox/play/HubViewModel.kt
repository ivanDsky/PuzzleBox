package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.playTarget
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One hub card: a playable game, its display name, its [brand], today's puzzle number
 * (null when the day has none or isn't cached yet) and today's status (null = not yet known).
 */
internal data class HubRow(
    val game: GameId,
    val name: String,
    val brand: GameBrand = GameBrand.Neutral,
    val puzzleNumber: Int? = null,
    val status: TodayStatus? = null,
)

/**
 * The hub's screen state (A13): the [rows] in registry order, each game's best daily time
 * ([bestByGame], null when never solved), how many of today's puzzles are solved
 * ([doneCount]) and the [next] game — the one card whose button is filled (null when
 * every game is solved or no status is known yet).
 */
internal data class HubUiState(
    val rows: List<HubRow>,
    val bestByGame: Map<GameId, Long?> = emptyMap(),
    val doneCount: Int = 0,
    val next: GameId? = null,
)

/**
 * The games hub's state holder: the [PlaySessionStore]'s playable games in registry order,
 * each with its [ObserveTodaySet] status, today's puzzle number (from [ObserveGameHistory]
 * on the game's [GameCalendar] day) and its best time ([ObserveGameStats], dailies only)
 * (A13, A14, SC11). The next game is the play target ([playTarget]) among the games that
 * can load, unless it is already solved. Before the first emission no row has a status.
 * The sources are collected only while the hub is observed (plus a 5 s grace), so a hub
 * left in a back stack across a deep sleep re-reads the day's cues when it shows again.
 * Each time the hub starts, [syncToday] prefetches today's uncached puzzles
 * ([SyncTodayPuzzles], A12), so a game that can't load reads Offline. Building the
 * ViewModel fetches nothing.
 */
class HubViewModel(
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    private val syncTodayPuzzles: SyncTodayPuzzles,
    stats: ObserveGameStats,
    history: ObserveGameHistory,
    calendar: GameCalendar,
    brands: GamePlayRegistry,
) : ViewModel() {

    private val seed = store.playable.map { (game, name) -> HubRow(game, name, brands.brandOf(game)) }

    private val bests: Flow<Map<GameId, Long?>> =
        if (seed.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(seed.map { row -> stats(row.game).map { row.game to it.bestTimeMs } }) { it.toMap() }
        }

    // Seeded empty, like the bests, so the cards never wait on history to show today's set.
    private val numbers: Flow<Map<GameId, Int>> = history()
        .map { days ->
            seed.mapNotNull { row ->
                val today = calendar.today(row.game)
                days.firstOrNull { it.gameId == row.game && it.day == today }?.puzzleNumber?.let { row.game to it }
            }.toMap()
        }
        .onStart { emit(emptyMap()) }

    internal val uiState: StateFlow<HubUiState> =
        combine(todaySet(), numbers, bests.onStart { emit(emptyMap()) }) { entries, number, best ->
            val status = entries.associate { it.game to it.status }
            HubUiState(
                rows = seed.map { it.copy(puzzleNumber = number[it.game], status = status[it.game]) },
                bestByGame = best,
                doneCount = seed.count { status[it.game] is TodayStatus.Solved },
                next = nextOf(entries.filter { entry -> seed.any { it.game == entry.game } }),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HubUiState(seed))

    /** Fetches today's uncached puzzles; a failure shows as that game's Offline card. */
    internal fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    private fun nextOf(entries: List<TodayEntry>): GameId? {
        val loadable = entries.filter { it.status != TodayStatus.Offline }
        val target = loadable.playTarget() ?: return null
        return target.takeIf { game -> loadable.first { it.game == game }.status !is TodayStatus.Solved }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
