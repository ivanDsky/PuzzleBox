package com.ivandsky.puzzlebox.insights.hub

import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod

/** What the Insights hub shows: the periods that have a recap, or why there are none. */
internal sealed interface InsightsHubUiState {

    /** The first read is still coming. */
    data object Loading : InsightsHubUiState

    /** Nothing solved yet, and this account's first cloud restore is still pulling: the history is coming back (A23). */
    data object Restoring : InsightsHubUiState

    /** Nothing solved yet. */
    data object Empty : InsightsHubUiState

    /** The read failed; the player can read again. Their solves are untouched. */
    data object Error : InsightsHubUiState

    /**
     * The years and then the months with a recap, newest first, each with its summary. [opened] holds the ids of the
     * periods whose story was watched, so a tile can read "Watched". [yearSoFar] is the current year when its recap is
     * listed before the year is over, null otherwise.
     */
    data class Ready(
        val periods: List<SummarizedPeriod>,
        val opened: Set<String> = emptySet(),
        val yearSoFar: Int? = null,
    ) : InsightsHubUiState
}
