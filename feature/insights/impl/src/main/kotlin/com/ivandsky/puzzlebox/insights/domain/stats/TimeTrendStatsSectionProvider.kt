package com.ivandsky.puzzlebox.insights.domain.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The time trend section, read off the facts' per-day tallies; left out when the range has no counted solve. */
internal object TimeTrendStatsSectionProvider : StatsSectionProvider {

    override fun section(input: StatsInput): Flow<StatsSection?> {
        val series = TimeTrendSeries.of(input.facts, input.range)
        return flowOf(if (series.points.isEmpty()) null else StatsSection.TimeTrend(SectionState.Ready(series)))
    }
}
