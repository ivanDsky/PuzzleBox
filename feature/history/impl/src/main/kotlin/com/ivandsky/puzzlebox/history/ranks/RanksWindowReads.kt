package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.domain.ranks.ObserveWindowRefresh
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The hub's one read of the window's boards, and how it is refreshed: [boards] is the single
 * collection (A23), tagged with the window it answers so a switch never shows the old window's
 * boards as the new one's (null until the first answer). Reconnects and the shared backoff (A24)
 * come from [ObserveWindowRefresh], which also hears [pull]; the answers it reads from are the
 * ones [boards] shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksWindowReads(
    observeRankWindow: ObserveRankWindow,
    observeWindowRefresh: ObserveWindowRefresh,
    window: Flow<RankWindow>,
    games: List<GameId>,
    private val scope: CoroutineScope,
) {
    private val pulls = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val shown = MutableStateFlow<RankWindowBoards>(RankWindowBoards.SignedOut)
    private val _refreshing = MutableStateFlow(false)
    private val ticks = observeWindowRefresh(pulls, shown)

    val boards: StateFlow<Pair<RankWindow, RankWindowBoards>?> = window
        .flatMapLatest { window -> observeRankWindow(window, games, ticks).onEach { shown.value = it }.map { window to it } }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** True while a [pull]'s indicator shows: a pull has no answer of its own, since a board that fails again looks unchanged. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** The pull-to-refresh gesture: retry what failed now, and show the indicator briefly. */
    fun pull() {
        pulls.tryEmit(Unit)
        scope.launch {
            _refreshing.value = true
            delay(INDICATOR_MS)
            _refreshing.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val INDICATOR_MS = 800L
    }
}
