package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.model.GameId

/** The stats page for a [scope] (one game, or every game when null) over a [range]: its [sections], in drawing order. Empty when the range has nothing to show. */
internal data class StatsPage(
    val scope: GameId?,
    val range: StatsRange,
    val sections: List<StatsSection>,
)
