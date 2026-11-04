package com.ivandsky.puzzlebox.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ReplayOutcome
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

/** One-shot answers to a replay request (ticket 14 → 13; tango-05). */
sealed interface HistoryEvent {
    /** Play-route arg: a self-describing session blob (ADR-0005). */
    data class ReplayReady(val session: ByteArray) : HistoryEvent

    /** The day is no longer in the cache, or its game is unregistered. */
    data object ReplayMissing : HistoryEvent

    /** The cached payload failed the engine decode — never playable (A16). */
    data class ReplayUnreadable(val error: DecodeError) : HistoryEvent
}

/**
 * History's state holder: cache-only reads over the GAME-AGNOSTIC merged history
 * ([ObserveGameHistory] — every game's completion records UNION every registered game's
 * captured days; fix round 5, defect C). Rows render per GameId, named by :app's
 * [GameRegistry] (the same lookup Home uses). The list pages lazily from the same
 * merged emissions the state is built from — a paging generation is never built from a
 * pre-emission empty list.
 *
 * Replay rides the daily cache, so only rows whose game has a captured day offer the
 * action; records key by game+day (ADR-0004: two games share the calendar).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    observeGameHistory: ObserveGameHistory,
    private val computeStreak: ComputeStreak,
    private val getReplaySession: GetReplaySession,
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

    private val replayEvents = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = replayEvents.receiveAsFlow()

    /** Builds the (game, day) replay from the daily cache and answers on [events]. */
    fun onReplay(game: GameId, day: LocalDate) {
        val puzzleNumber = uiState.value.rows.firstOrNull { it.gameId == game && it.day == day }?.puzzleNumber
        viewModelScope.launch {
            replayEvents.send(
                when (val outcome = getReplaySession(game, day, puzzleNumber)) {
                    is ReplayOutcome.Ready -> HistoryEvent.ReplayReady(outcome.session)
                    ReplayOutcome.Missing -> HistoryEvent.ReplayMissing
                    is ReplayOutcome.Unreadable -> HistoryEvent.ReplayUnreadable(outcome.error)
                },
            )
        }
    }
}
