package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One hub row: a playable game, its display name and today's status (null = not yet known). */
internal data class HubRow(
    val game: GameId,
    val name: String,
    val status: TodayStatus? = null,
)

/**
 * The games hub's state holder: the [PlaySessionStore]'s playable games in registry order,
 * each with its [ObserveTodaySet] status (A13, A14, SC11). Before the first emission no
 * row has one. The set is collected only while the hub is observed (plus a 5 s grace), so
 * a hub left in a back stack across a deep sleep re-reads the day's cues when it shows again.
 * Each time the hub starts, [syncToday] prefetches today's uncached puzzles
 * ([SyncTodayPuzzles], A12), so a game that can't load reads Offline. Building the
 * ViewModel fetches nothing.
 */
class HubViewModel(
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    private val syncTodayPuzzles: SyncTodayPuzzles,
) : ViewModel() {

    private val playable = store.playable

    internal val rows: StateFlow<List<HubRow>> = todaySet()
        .map { entries ->
            val status = entries.associate { it.game to it.status }
            playable.map { (game, name) -> HubRow(game, name, status[game]) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            playable.map { (game, name) -> HubRow(game, name) },
        )

    /** Fetches today's uncached puzzles; a failure shows as that game's Offline row. */
    internal fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
