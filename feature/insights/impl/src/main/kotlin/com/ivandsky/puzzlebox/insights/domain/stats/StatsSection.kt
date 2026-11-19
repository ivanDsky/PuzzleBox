package com.ivandsky.puzzlebox.insights.domain.stats

/**
 * One section of the stats page with its own load state (A16). The page lists its sections in the order the screen draws
 * them; a section with nothing to say is not in the list at all. A later section is one more subtype here, one provider
 * ([StatsSectionProvider]) and one line each in [StatsSectionProviders] and the screen's slot.
 */
internal sealed interface StatsSection {

    /** Fastest per game, longest streak and longest hint-free run. Local, so always ready. */
    data class Records(val state: SectionState<StatsRecords>) : StatsSection
}
