package com.ivandsky.puzzlebox.insights.domain.stats

/**
 * The stats page's sections in the order the page draws them (spec 2.4): time trend, Records, hint-free rate over time,
 * weekday pattern, rank history, vs friends. [vsFriends] is the one provider that reads the network, so it comes in
 * from the wiring. The rank history ticket adds its provider at its place here with one line; until then the slot is
 * absent and the page shows nothing for it.
 */
internal fun statsSectionProviders(vsFriends: StatsSectionProvider): List<StatsSectionProvider> = listOf(
    TimeTrendStatsSectionProvider,
    RecordsStatsSectionProvider,
    HintFreeStatsSectionProvider,
    WeekdayStatsSectionProvider,
    vsFriends,
)
