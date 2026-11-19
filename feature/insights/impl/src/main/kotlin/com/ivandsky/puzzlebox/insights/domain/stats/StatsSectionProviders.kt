package com.ivandsky.puzzlebox.insights.domain.stats

/**
 * The stats page's sections in the order the page draws them (spec 2.4): time trend, Records, hint-free rate over time,
 * weekday pattern, rank history, vs friends. [rankHistory] and [vsFriends] are the providers that read kept weeks or
 * the network, so they come in from the wiring.
 */
internal fun statsSectionProviders(rankHistory: StatsSectionProvider, vsFriends: StatsSectionProvider): List<StatsSectionProvider> = listOf(
    TimeTrendStatsSectionProvider,
    RecordsStatsSectionProvider,
    HintFreeStatsSectionProvider,
    WeekdayStatsSectionProvider,
    rankHistory,
    vsFriends,
)
