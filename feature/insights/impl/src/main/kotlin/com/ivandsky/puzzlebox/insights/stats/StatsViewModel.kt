package com.ivandsky.puzzlebox.insights.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.insights.domain.stats.ObserveStatsPage
import com.ivandsky.puzzlebox.insights.domain.stats.ObserveStatsSince
import com.ivandsky.puzzlebox.insights.domain.stats.StatsPage
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The stats page: the range and the game picked, and the sections read for them ([ObserveStatsPage]). The page is
 * live, so a solve that lands while it is open updates it; the first cloud restore shows as [StatsBody.Restoring]
 * while the page has nothing (A23).
 *
 * Saved state holds ids only: the range's name and the game's id. It wins over [initialGame], the game the page was
 * opened on, so a recreated page stays on what the player picked. A game this build does not list reads as every game.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StatsViewModel(
    initialGame: GameId?,
    private val observeStatsPage: ObserveStatsPage,
    private val registry: GameRegistry,
    private val cloudSync: CloudSync,
    private val savedState: SavedStateHandle,
    private val observeSince: ObserveStatsSince,
) : ViewModel() {

    private val range: StateFlow<String> = savedState.getStateFlow(RANGE_KEY, DEFAULT_RANGE.name)
    private val game: StateFlow<String?> = savedState.getStateFlow(GAME_KEY, initialGame?.value)

    val uiState: StateFlow<StatsUiState> = combine(range, game) { range, game -> Selection(readRange(range), readGame(game)) }
        .flatMapLatest { selection ->
            combine(observeStatsPage(selection.game, selection.range, allTimeRecords = true), cloudSync.restoring, observeSince(selection.game)) { page, restoring, since ->
                StatsUiState(selection.range, selection.game, body(page, restoring), since)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, StatsUiState.loading(readRange(range.value), readGame(game.value)))

    /** The player picked [range]. */
    fun selectRange(range: StatsRange) {
        savedState[RANGE_KEY] = range.name
    }

    /** The player picked [game], or every game with null. */
    fun selectGame(game: GameId?) {
        savedState[GAME_KEY] = game?.value
    }

    private fun body(page: StatsPage, restoring: Boolean): StatsBody = when {
        page.sections.isNotEmpty() -> StatsBody.Ready(page.sections)
        restoring -> StatsBody.Restoring
        else -> StatsBody.Empty
    }

    private fun readRange(id: String?): StatsRange = StatsRange.parse(id) ?: DEFAULT_RANGE

    private fun readGame(id: String?): GameId? = id?.let(::GameId)?.takeIf { registry[it] != null }

    private data class Selection(val range: StatsRange, val game: GameId?)

    private companion object {
        const val RANGE_KEY = "stats.range"
        const val GAME_KEY = "stats.game"
        val DEFAULT_RANGE = StatsRange.Week
    }
}
