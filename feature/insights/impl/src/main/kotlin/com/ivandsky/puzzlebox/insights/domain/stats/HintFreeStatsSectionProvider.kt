package com.ivandsky.puzzlebox.insights.domain.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The hint-free rate section, read off the facts' per-day tallies; left out when the range has no counted solve. */
internal object HintFreeStatsSectionProvider : StatsSectionProvider {

    override fun section(input: StatsInput): Flow<StatsSection?> {
        val series = HintFreeRateSeries.of(input.facts, input.range)
        return flowOf(if (series.points.isEmpty()) null else StatsSection.HintFreeRate(SectionState.Ready(series)))
    }
}
