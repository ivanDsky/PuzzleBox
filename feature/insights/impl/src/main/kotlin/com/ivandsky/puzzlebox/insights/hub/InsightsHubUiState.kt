package com.ivandsky.puzzlebox.insights.hub

import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod

/** What the Insights hub shows: the months that have a recap, or why there are none. */
internal sealed interface InsightsHubUiState {

    /** The first read is still coming. */
    data object Loading : InsightsHubUiState

    /** Nothing solved yet, and this account's first cloud restore is still pulling: the history is coming back (A23). */
    data object Restoring : InsightsHubUiState

    /** Nothing solved yet. */
    data object Empty : InsightsHubUiState

    /** The months with a recap, newest first. */
    data class Ready(val periods: List<RecapPeriod>) : InsightsHubUiState
}
