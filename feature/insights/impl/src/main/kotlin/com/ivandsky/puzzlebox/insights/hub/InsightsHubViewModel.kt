package com.ivandsky.puzzlebox.insights.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecapAvailability
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The hub's list: the years and months with a recap, live, so a solve or a restore adds its period while the hub is
 * open. Year rows are there only while [InsightsFlag.YearRecap] is on. An empty list reads "coming back" while the
 * first cloud restore runs and "nothing yet" otherwise (A23).
 */
internal class InsightsHubViewModel(
    observeRecapAvailability: ObserveRecapAvailability,
    cloudSync: CloudSync,
    releaseFlags: ReleaseFlags,
) : ViewModel() {

    val uiState: StateFlow<InsightsHubUiState> =
        combine(observeRecapAvailability(), cloudSync.restoring, releaseFlags.enabled) { available, restoring, enabled ->
            val periods = if (InsightsFlag.YearRecap in enabled) available else available.filter { it !is RecapPeriod.Year }
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
