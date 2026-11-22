package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSince
import com.ivandsky.puzzlebox.model.GameId

/**
 * What the stats page shows: the [range] chip and [game] picked (null is every game) and the [body] read for them.
 * [filters] is the StatsFilters release flag, read at the view model (ADR-0022): the filter bar replaces the chips.
 * [since] is the puzzle day and first solve its Since rows are worded from, null with the flag off or until read.
 */
internal data class StatsUiState(
    val range: StatsRange,
    val game: GameId?,
    val body: StatsBody,
    val filters: Boolean = false,
    val since: StatsSince? = null,
) {

    companion object {
        fun loading(
            range: StatsRange = StatsRange.Month,
            game: GameId? = null,
            filters: Boolean = false,
            since: StatsSince? = null,
        ) = StatsUiState(range, game, StatsBody.Loading, filters, since)
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
