package com.ivandsky.puzzlebox.insights.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.data.insights.RecapOpenings
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecapAvailability
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The hub's list: the years and months with a recap and their summaries, live, so a solve or a restore adds its period
 * while the hub is open, and a watched story flips its tile to "Watched" when the player comes back. An empty list reads "coming back" while the first
 * cloud restore runs and "nothing yet" otherwise (A23). A read that fails is [InsightsHubUiState.Error], logged; [retry] reads again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class InsightsHubViewModel(
    observeRecapAvailability: ObserveRecapAvailability,
    cloudSync: CloudSync,
    openings: RecapOpenings,
    puzzleDay: GetPuzzleDay,
    private val failures: FailureLog = LogcatFailureLog,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<InsightsHubUiState> = attempt
        .flatMapLatest {
            combine(observeRecapAvailability(), cloudSync.restoring, openings.observe()) { periods, restoring, opened ->
                val thisYear = puzzleDay().year.takeIf { year -> periods.any { it.period == RecapPeriod.Year(year) } }
                when {
                    periods.isNotEmpty() -> InsightsHubUiState.Ready(periods, opened, thisYear)
                    restoring -> InsightsHubUiState.Restoring
                    else -> InsightsHubUiState.Empty
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                failures.warn(TAG, "hub read failed", failure)
                emit(InsightsHubUiState.Error)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsHubUiState.Loading)

    /** Reads again after an [InsightsHubUiState.Error]. */
    fun retry() = attempt.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TAG = "InsightsHub"
    }
}
