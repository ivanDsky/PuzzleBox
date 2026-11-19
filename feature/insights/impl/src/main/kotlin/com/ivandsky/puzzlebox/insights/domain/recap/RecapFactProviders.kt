package com.ivandsky.puzzlebox.insights.domain.recap

/**
 * Every provider of the recap chain: the one place a provider is registered, and the order of its cards. A new fact is
 * its own file plus one line here.
 */
internal val RecapFactProviders: List<RecapFactProvider> = listOf(
    SolvedRecapFactProvider,
    FastestRecapFactProvider,
    StreakRecapFactProvider,
    HintFreeRecapFactProvider,
    MomentsRecapFactProvider,
    FriendsRecapFactProvider,
    TotalTimeRecapFactProvider,
)
