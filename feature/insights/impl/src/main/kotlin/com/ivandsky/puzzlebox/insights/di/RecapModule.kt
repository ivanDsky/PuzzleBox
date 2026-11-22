package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall
import com.ivandsky.puzzlebox.insights.domain.rankhistory.ObserveRankHistory
import com.ivandsky.puzzlebox.insights.domain.recap.ComputePeriodFacts
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveDueRecap
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecap
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecapAvailability
import com.ivandsky.puzzlebox.insights.domain.recap.ResolveFriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.ReplaySolveFacts
import com.ivandsky.puzzlebox.insights.recap.RecapViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The recap's wiring, included by [insightsModule]: its use cases and the story's view model. The friends standing
 * ([ResolveFriendsStanding]) is the one recap read that goes to the network for a month, kept apart from the local
 * providers; a year's comes from the kept weekly history ([ObserveRankHistory]). [ObserveDueRecap] is the one use case
 * :app reads, for Home's recap card. A story's period arrives as a Koin parameter, so each opened month or year has its own.
 */
val recapModule = module {
    factory { ReplaySolveFacts(computeStreak = get(), puzzleDay = get(), registry = get()) }
    factory { ComputePeriodFacts(computeStreak = get(), replaySolveFacts = get(), registry = get()) }
    factory {
        val friendsOverall = get<ObserveFriendsOverall>()
        val rankHistory = get<ObserveRankHistory>()
        ResolveFriendsStanding(
            identity = get(),
            friendsOverall = { window, games -> friendsOverall(window, games) },
            rankHistory = { game -> rankHistory(game) },
            puzzleDay = get(),
        )
    }
    factory {
        ObserveRecap(observeGameHistory = get(), computePeriodFacts = get(), registry = get(), resolveFriends = get())
    }
    factory { ObserveRecapAvailability(observeGameHistory = get(), calendar = get(), registry = get()) }
    factory { ObserveDueRecap(availability = get()) }

    viewModel { params ->
        RecapViewModel(period = params.get(), observeRecap = get(), cloudSync = get(), puzzleDay = get(), openings = get(), savedState = get())
    }
}
