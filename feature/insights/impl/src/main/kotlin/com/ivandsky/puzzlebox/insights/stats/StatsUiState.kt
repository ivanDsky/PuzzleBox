package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.model.GameId

/** What the stats page shows: the [range] chip and [game] picked (null is every game) and the [body] read for them. */
internal data class StatsUiState(val range: StatsRange, val game: GameId?, val body: StatsBody) {

    companion object {
        fun loading(range: StatsRange = StatsRange.Month, game: GameId? = null) = StatsUiState(range, game, StatsBody.Loading)
    }
}

/** What the page shows under the range chips. */
internal sealed interface StatsBody {

    /** The sections are being read. */
    data object Loading : StatsBody

    /** The range has nothing yet, and this account's first cloud restore is still pulling: the history is coming back (A23). */
    data object Restoring : StatsBody

    /** The range has no solved puzzle. */
    data object Empty : StatsBody

    /** The page's [sections], in drawing order. */
    data class Ready(val sections: List<StatsSection>) : StatsBody
}
