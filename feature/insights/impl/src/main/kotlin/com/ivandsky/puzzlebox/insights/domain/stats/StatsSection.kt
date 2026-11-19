package com.ivandsky.puzzlebox.insights.domain.stats

/**
 * One section of the stats page with its own load state (A16). The page lists its sections in the order the screen draws
 * them; a section with nothing to say is not in the list at all. A later section is one more subtype here, one provider
 * ([StatsSectionProvider]) and one line each in [StatsSectionProviders] and the screen's slot.
 */
internal sealed interface StatsSection {

    /** The average solve time per week (per day for the Week range). Local, so always ready. */
    data class TimeTrend(val state: SectionState<TimeTrendSeries>) : StatsSection

    /** Fastest per game, longest streak and longest hint-free run. Local, so always ready. */
    data class Records(val state: SectionState<StatsRecords>) : StatsSection

    /** The share of hint-free solves per week (per day for the Week range). Local, so always ready. */
    data class HintFreeRate(val state: SectionState<HintFreeRateSeries>) : StatsSection
}
