package com.ivandsky.puzzlebox.insights.domain.stats

/**
 * One section of the stats page with its own load state (A16). The page lists its sections in the order the screen draws
 * them; a section with nothing to say is not in the list at all. A later section is one more subtype here, one provider
 * ([StatsSectionProvider]) and one line each in [statsSectionProviders] and the screen's slot.
 */
internal sealed interface StatsSection {

    /** The average solve time per week (per day for the Week range). Local, so always ready. */
    data class TimeTrend(val state: SectionState<TimeTrendSeries>) : StatsSection

    /** Fastest per game, longest streak and longest hint-free run. Local, so always ready. */
    data class Records(val state: SectionState<StatsRecords>) : StatsSection

    /** The share of hint-free solves per week (per day for the Week range). Local, so always ready. */
    data class HintFreeRate(val state: SectionState<HintFreeRateSeries>) : StatsSection

    /** The average solve time per weekday of the range. Local, so always ready. */
    data class Weekday(val state: SectionState<WeekdayPattern>) : StatsSection

    /** The viewer's Friends points per kept week, whatever the range. Reads this device's kept weeks and, with none, the network (A16); signed out or without friends it is one plain line. */
    data class RankHistory(val state: SectionState<RankHistoryStanding>) : StatsSection

    /** The best and weakest game among friends. Needs the network (A16), so it loads and can be unavailable; signed out or without friends it is one plain line. */
    data class VsFriends(val state: SectionState<VsFriendsStanding>) : StatsSection
}
