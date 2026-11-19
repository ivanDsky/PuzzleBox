package com.ivandsky.puzzlebox.insights.domain.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The weekday pattern section, read off the facts' per-day tallies; left out when the range has no counted solve. */
internal object WeekdayStatsSectionProvider : StatsSectionProvider {

    override fun section(input: StatsInput): Flow<StatsSection?> {
        val pattern = WeekdayPattern.of(input.facts)
        return flowOf(if (pattern.days.isEmpty()) null else StatsSection.Weekday(SectionState.Ready(pattern)))
    }
}
