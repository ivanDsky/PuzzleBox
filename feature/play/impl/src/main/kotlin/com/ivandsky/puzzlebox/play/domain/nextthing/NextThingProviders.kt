package com.ivandsky.puzzlebox.play.domain.nextthing

/**
 * Every provider of the next-thing chain: the one place a provider is registered. A new one is its own
 * file plus one line here; its [NextThingProvider.order] decides where it is asked, not its line.
 */
internal val NextThingProviders: List<NextThingProvider> = listOf(
    NextUnsolvedDailyNextThingProvider,
    ChallengeFriendNextThingProvider,
    MissedDayNextThingProvider,
    NewFriendPuzzleNextThingProvider,
    CommunityNextThingProvider,
    DoneForTodayNextThingProvider,
)
