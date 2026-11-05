package com.ivandsky.puzzlebox.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * Everything History renders besides the paged list: every merged row (the count and
 * the replay identity lookup), each (game, day)'s record, and the streak teaser — all
 * of which must see every captured/solved day, not just the loaded pages.
 */
data class HistoryUiState(
    val rows: List<HistoryDayRow> = emptyList(),
    val recordsByKey: Map<Pair<GameId, LocalDate>, CompletionRecord> = emptyMap(),
    val streak: Int = 0,
)

/**
 * History's state holder: cache-only reads over the GAME-AGNOSTIC merged history
 * ([ObserveGameHistory] — every game's completion records UNION every registered game's
 * captured days; fix round 5, defect C). Rows render per GameId, named by :app's
 * [GameRegistry] (the same lookup Home uses). The list pages lazily from the same
 * merged emissions the state is built from — a paging generation is never built from a
 * pre-emission empty list.
 *
 * Replay is navigation only (ticket 11): the route reports the (game, day) and the play
 * store opens that day's slot — History never builds a session blob. Records key by
 * game+day (ADR-0004: two games share the calendar).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    observeGameHistory: ObserveGameHistory,
    private val computeStreak: ComputeStreak,
    private val registry: GameRegistry,
) : ViewModel() {

    private val history: Flow<HistoryUiState> = observeGameHistory()
        .map { merged ->
            val rows = merged.map { it.toRow(registry.displayName(it.gameId)) }.sortedWith(HISTORY_ROW_ORDER)
            HistoryUiState(
                rows = rows,
                recordsByKey = merged.mapNotNull { day -> day.record?.let { (day.gameId to day.day) to it } }.toMap(),
                streak = computeStreak(rows.map(HistoryDayRow::day)),
            )
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<HistoryUiState> =
        history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    val pagedDays: Flow<PagingData<HistoryDayRow>> =
        history.flatMapLatest { historyPager(it.rows) }.cachedIn(viewModelScope)
}
