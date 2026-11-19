package com.ivandsky.puzzlebox.insights.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecapAvailability
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The hub's list: the months with a recap, live, so a solve or a restore adds its month while the hub is open. An
 * empty list reads "coming back" while the first cloud restore runs and "nothing yet" otherwise (A23).
 */
internal class InsightsHubViewModel(
    observeRecapAvailability: ObserveRecapAvailability,
    cloudSync: CloudSync,
) : ViewModel() {

    val uiState: StateFlow<InsightsHubUiState> =
        combine(observeRecapAvailability(), cloudSync.restoring) { periods, restoring ->
            when {
                periods.isNotEmpty() -> InsightsHubUiState.Ready(periods)
                restoring -> InsightsHubUiState.Restoring
                else -> InsightsHubUiState.Empty
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsHubUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
